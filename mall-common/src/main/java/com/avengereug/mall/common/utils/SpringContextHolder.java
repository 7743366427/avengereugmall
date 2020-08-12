package com.avengereug.mall.common.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static<T> T getProperty(String key) {
        return (T)applicationContext.getEnvironment().getProperty(key);
    }

    public static <T> T getBean(Class<?> clazz) {
        return (T)getApplicationContext().getBean(clazz);
    }

    public static <T> T getBean(String beanName) {
        return (T)getApplicationContext().getBean(beanName);
    }

}
