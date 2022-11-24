package com.zzw.spring.source.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

/**
 * @author Wen
 * @date 2022/11/23 18:10
 */
@Component
public class PropertyBean {
    @Value("${test.name}")
    private String name;
    @Autowired
    private PropertyBeanA propertyBeanA;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "PropertyBean{" +
                "name='" + name + '\'' +
                ", propertyBeanA=" + propertyBeanA +
                '}';
    }
}
