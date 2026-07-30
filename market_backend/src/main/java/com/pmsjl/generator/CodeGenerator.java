package com.pmsjl.generator;


import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;


//mybatisplus模板生成
public class CodeGenerator {

    public static void main(String[] args) {

        FastAutoGenerator.create(
                        // ==================== 数据库配置 ====================
                        requireEnvironmentVariable("DB_URL"),
                        requireEnvironmentVariable("DB_USERNAME"),
                        requireEnvironmentVariable("DB_PASSWORD")
                )
                .globalConfig(builder -> {
                    builder.author("pmsjl")           // 作者名
                            .outputDir(requireEnvironmentVariable("CODEGEN_JAVA_OUTPUT_DIR"))
                            .disableOpenDir();           // 生成后不自动打开文件夹
                })

                .packageConfig(builder -> {
                    builder.parent("com.pmsjl")      // 父包名
                            .entity("model.entity")               // entity 包名
                            .mapper("mapper")               // mapper 包名
                            .service("service")             // service 接口包名
                            .serviceImpl("service.Impl")    // service 实现类包名
                            .controller("controller")       // controller 包名
                            .pathInfo(Collections.singletonMap(OutputFile.xml,
                                    requireEnvironmentVariable("CODEGEN_XML_OUTPUT_DIR")));
                })

                .strategyConfig(builder -> {
                    builder.addInclude("private_message")           // ← 这里写你要生成的表名（可多个，用逗号分隔）
                            .addTablePrefix("")             // 如果表有前缀如 tb_，这里填写 tb_
                            .entityBuilder()
                            .enableLombok()             // 开启 Lombok
                            .enableTableFieldAnnotation() // 字段上加 @TableField
                            .controllerBuilder()
                            .enableRestStyle()          // 生成 @RestController
                            .enableHyphenStyle()        // url 使用连字符
                            .serviceBuilder()
                            .formatServiceFileName("%sService")     // 生成 IStudentService
                            .formatServiceImplFileName("%sServiceImpl");
                })

                .templateEngine(new VelocityTemplateEngine())
                .execute();
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
