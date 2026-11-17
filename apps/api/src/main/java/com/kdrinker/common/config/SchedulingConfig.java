package com.kdrinker.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업(앨범 마감)을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
