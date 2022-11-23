package com.zzw.spring.source.bean;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author Wen
 * @date 2022/11/23 18:08
 */

@Configuration
@ComponentScan("com.zzw.spring.source.bean")
@PropertySource(value = "classpath:test.properties", name = "testProperty")
public class TestProperty {

}
