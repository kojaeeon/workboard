package com.kojaeeon.dashboard.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticFilesConfig implements WebMvcConfigurer {

  private final Path images;

  public StaticFilesConfig(@Value("${DASHBOARD_IMAGES_PATH:images}") String images)
    throws IOException {
    this.images = Path.of(images).toAbsolutePath().normalize();
    Files.createDirectories(this.images);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/images/**").addResourceLocations(images.toUri().toString());
  }
}
