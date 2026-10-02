package com.pmsjl.utils;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WordUtilsTest {
    private static final String CLASS_NAME = "com.pmsjl.utils.WordUtils";
    private static final String CLASS_ENTRY = "com/pmsjl/utils/WordUtils.class";
    private static final String WORDS = "forbiddenWords.txt";
    private static final String DICTIONARY = "  测试违禁词  \n \n\t\n另一个词\n";

    @TempDir
    Path temp;

    @Test
    void actualClasspathDictionaryDetectsAndExtractsForbiddenWords() throws IOException {
        String word;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(WORDS))) {
            word = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).findFirst().orElseThrow();
        }
        assertFalse(WordUtils.containsForbiddenWords("这篇经验很有帮助"));
        assertTrue(WordUtils.containsForbiddenWords(word));
        assertTrue(WordUtils.extractForbiddenWords(word).contains(word));
    }

    @Test
    void directoryResourceReadsUtf8AndTrimsBlankLines() throws Exception {
        Path directory = directory(true);
        try (var loader = new ResourceLoader(directory.toUri().toURL(), null)) {
            assertEquals("file", loader.getResource(WORDS).getProtocol());
            assertDictionary(loader);
        }
    }

    @Test
    void jarResourceWorksOnRepeatedCallsWithoutInitializationFailure() throws Exception {
        Path jar = temp.resolve("dictionary.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(CLASS_ENTRY));
            output.write(classBytes());
            output.closeEntry();
            output.putNextEntry(new JarEntry(WORDS));
            output.write(DICTIONARY.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var loader = new ResourceLoader(jar.toUri().toURL(), null)) {
            assertEquals("jar", loader.getResource(WORDS).getProtocol());
            assertDictionary(loader);
            assertDictionary(loader);
        }
    }

    @Test
    void missingResourceFailsClosedWithoutFallingBackToParentDictionary() throws Exception {
        try (var loader = new ResourceLoader(directory(false).toUri().toURL(), null)) {
            assertNull(loader.getResource(WORDS));
            assertInitializationFailure(loader);
            assertThrows(NoClassDefFoundError.class, () -> initialize(loader));
        }
    }

    @Test
    void midReadFailureRejectsPartialDictionaryAndClosesStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Supplier<InputStream> failing = () -> new InputStream() {
            private final ByteArrayInputStream prefix = new ByteArrayInputStream("测试违禁词\n".getBytes(StandardCharsets.UTF_8));

            @Override
            public int read() throws IOException {
                if (prefix.available() == 0) throw new IOException("simulated dictionary read failure");
                return prefix.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) return 0;
                if (prefix.available() == 0) throw new IOException("simulated dictionary read failure");
                return prefix.read(bytes, offset, length);
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        try (var loader = new ResourceLoader(directory(true).toUri().toURL(), failing)) {
            assertInitializationFailure(loader);
            assertTrue(closed.get());
        }
    }

    @Test
    void successfulReadClosesResourceStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Supplier<InputStream> tracked = () -> new ByteArrayInputStream(DICTIONARY.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        try (var loader = new ResourceLoader(directory(true).toUri().toURL(), tracked)) {
            assertDictionary(loader);
            assertTrue(closed.get());
        }
    }

    private void assertDictionary(ClassLoader loader) throws Exception {
        Class<?> type = initialize(loader);
        var contains = type.getMethod("containsForbiddenWords", String.class);
        assertEquals(false, contains.invoke(null, "正常评论内容"));
        assertEquals(false, contains.invoke(null, " \t"));
        assertEquals(true, contains.invoke(null, "前缀测试违禁词后缀"));
        assertEquals(List.of("测试违禁词"), type.getMethod("extractForbiddenWords", String.class)
                .invoke(null, "前缀测试违禁词后缀"));
    }

    private void assertInitializationFailure(ClassLoader loader) {
        ExceptionInInitializerError error = assertThrows(ExceptionInInitializerError.class,
                () -> initialize(loader));
        BusinessException cause = assertInstanceOf(BusinessException.class, error.getCause());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), cause.getCode());
        assertEquals("读取违禁词文件出错", cause.getMessage());
    }

    private Class<?> initialize(ClassLoader loader) throws ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            return Class.forName(CLASS_NAME, true, loader);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private Path directory(boolean includeWords) throws IOException {
        Path directory = Files.createTempDirectory(temp, "dictionary-");
        Path classFile = directory.resolve(CLASS_ENTRY);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, classBytes());
        if (includeWords) Files.writeString(directory.resolve(WORDS), DICTIONARY, StandardCharsets.UTF_8);
        return directory;
    }

    private byte[] classBytes() throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(CLASS_ENTRY))) {
            return input.readAllBytes();
        }
    }

    /** Isolate WordUtils and its dictionary, but share dependencies with the test runtime. */
    private static class ResourceLoader extends URLClassLoader {
        private final Supplier<InputStream> stream;

        ResourceLoader(URL resource, Supplier<InputStream> stream) {
            super(new URL[]{resource}, WordUtilsTest.class.getClassLoader());
            this.stream = stream;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!CLASS_NAME.equals(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }

        @Override
        public URL getResource(String name) {
            return WORDS.equals(name) ? findResource(name) : super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (WORDS.equals(name) && stream != null) return stream.get();
            return super.getResourceAsStream(name);
        }
    }
}
