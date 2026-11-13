package com.company.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 生产环境前端静态资源托管。
 * <p>
 * 将根路径 "/" 映射到外部目录 ${app.static.location}，
 * 实现前后端独立部署（前端构建产物可热替换，无需重启 jar）。
 * <p>
 * 仅在 prod profile 生效，避免影响本地开发联调。
 */
@Configuration
@Profile("prod")
public class StaticResourceConfig implements WebMvcConfigurer {

    /** 静态资源根目录，application-prod.yml 配置 app.static.location */
    @Value("${app.static.location}")
    private String staticLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意 file: 协议 + 末尾斜线必须有；/** 优先级低于 /api/**，不会拦截 API
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + staticLocation);
    }

    /**
     * 显式把 "/" 转发到 index.html。
     * Spring Boot 默认 welcome page 机制不支持外部 file: 路径，需要手动声明。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}

