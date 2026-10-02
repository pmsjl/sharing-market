package com.pmsjl.utils;

import cn.hutool.dfa.WordTree;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 内容工具类
 */
@Slf4j
public class WordUtils {
    private static final WordTree WORD_TREE;

    static {
        try (InputStream input = new ClassPathResource(
                "forbiddenWords.txt", WordUtils.class.getClassLoader()).getInputStream()) {
            List<String> blackList = loadBlackListFromStream(input);
            WordTree wordTree = new WordTree();
            wordTree.addWords(blackList);
            WORD_TREE = wordTree;
        } catch (IOException e) {
            log.error("读取违禁词文件时出错", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取违禁词文件出错");
        }
    }

    /**
     * 从资源流中加载违禁词列表，读取失败时不返回部分词库。
     *
     * @param input 违禁词资源流
     * @return 违禁词列表
     */
    private static List<String> loadBlackListFromStream(InputStream input) throws IOException {
        List<String> blackList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    blackList.add(word);
                }
            }
        }
        return blackList;
    }

    /**
     * 检测文本中是否包含违禁词
     *
     * @param content 输入文本
     * @return 是否包含违禁词
     */
    public static boolean containsForbiddenWords(String content) {
        return !WORD_TREE.matchAll(content).isEmpty();
    }

    /**
     * 提取文本中的违禁词列表
     *
     * @param content 输入文本
     * @return 检测到的违禁词列表
     */
    public static List<String> extractForbiddenWords(String content) {
        return WORD_TREE.matchAll(content);
    }

}
