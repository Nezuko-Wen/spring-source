package com.zzw.spring.source.bean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author Wen
 * @date 2022/11/23 18:12
 */
public class PropertyMain {

    public static final Log logger = LogFactory.getLog(PropertyMain.class);

    public static void main(String[] args) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        //获取 Spring 上下文， Spring 项目启动的入口
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestProperty.class);

        //第一部分，获取 @PropertySource, 并拿到注解配置的一些主要信息，如文件路径，编码格式等。

        //通过类信息，获取元数据，转化为元数据之后，能够更方便的访问类基本信息,类涉及的注解信息。
        AnnotationMetadata metadata = AnnotationMetadata.introspect((Class<?>) TestProperty.class);
        //获取 @PropertySource 注解的内容
        Map<String, Object> attributeMap = metadata.getAnnotationAttributes(PropertySource.class.getName(), false);
        AnnotationAttributes currentPropertySource = new AnnotationAttributes(attributeMap);
        //获取配置文件的路径
        String[] locations = currentPropertySource.getStringArray("value");
        System.out.println(Arrays.toString(locations));

        //获取资源管理器工厂类， 默认为PropertySourceFactory.class，可以通过该工厂指定文件路径，实例化文件 Spring 中对应的资源文件对象
        Class<? extends PropertySourceFactory> factoryClass = currentPropertySource.getClass("factory");
        //获取资源工厂实例
        PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
                new DefaultPropertySourceFactory() : BeanUtils.instantiateClass(factoryClass));
        //获取指定文件的名称，默认为空
        String name = currentPropertySource.getString("name");
        //获取文件的编码规则，默认为空
        String encoding = currentPropertySource.getString("encoding");
        if (!StringUtils.hasLength(encoding)) {
            encoding = null;
        }
        if (!StringUtils.hasLength(name)) {
            name = null;
        }

        //第二部分，实例化资源文件，加入 Spring 内部的资源列表中保存

        //获取当前配置环境实例
        ConfigurableEnvironment environment = context.getEnvironment();
        //获取配置文件在 Spring 中对应的对象列表
        MutablePropertySources propertySources = environment.getPropertySources();
        //自定义资源加载器
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        for (String location : locations) {
            //获取文件路径
            String resolvedLocation = environment.resolveRequiredPlaceholders(location);
            //加载资源文件
            Resource resource = resourceLoader.getResource(resolvedLocation);
            try {
                //通过工厂的方式创建资源对象
                org.springframework.core.env.PropertySource<?> propertySource1 = factory.createPropertySource(name, new EncodedResource(resource, encoding));
                //将新加载到的资源，加入到资源列表 propertySourceList 中，Spring内部，就是靠这个列表来保存资源文件的。
                propertySources.addLast(propertySource1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //第三部分，实例化 bean，doGetBean的前部分

        //首先实例化 bean， 即先实例化bean定义，再根据bean定义，最后通过构造器反射获取对象，这一部分简过，前序文章已经讲过
        RootBeanDefinition beanDefinition = new RootBeanDefinition(PropertyBean.class);
        //构造器反射获取实例 candidate.newInstance();
        Constructor<?>[] candidates = PropertyBean.class.getDeclaredConstructors();
        PropertyBean propertyBean = (PropertyBean) candidates[0].newInstance();
        System.out.println(propertyBean);
        //第四部分，并对其进行属性赋值

        //至此已经拿到了一个实例化的bean，但是还没有进行属性赋值，接下来就要做这件事，首先拿到所有标注了@Autowired, @Value或者@Inject注解的属性信息保存到fields列表里。
        List<Field> fields = new ArrayList<>();
        //通过反射工具类，遍历所有属性， 这里第二个参数就用到了函数型接口
        //在doWithLocalFields方法中调用fc.doWith(field)时，就会进入传入的lamda表达式函数里，
        ReflectionUtils.doWithLocalFields(PropertyBean.class, field -> {
            //findAutowiredAnnotation 用来判断，属性上是否标注了@Autowired, @Value或者@Inject注解
            MergedAnnotation<?> ann = findAutowiredAnnotation(field);
            if (ann != null) {
                //符合条件的参数，保存到 fields 中
                fields.add(field);
            }
        });
        //至此拿到了所有需要注入信息的属性
        for (Field field : fields) {

            //通过属性信息，实例化属性描述文件，用于更好的访问属性信息
            DependencyDescriptor desc = new DependencyDescriptor(field, false);
            //获取 @Value 引用的值，有兴趣可以看下 getSuggestedValue, 它指定获取 Value.class 类型的属性，就获取配置的值，如 ${test.name}
            Object value = new ContextAnnotationAutowireCandidateResolver().getSuggestedValue(desc);
            System.out.println("value:" + value);

            if (value != null) {
                if (value instanceof String) {
                    //先去除掉 ${}
                    String strValue = resolveEmbeddedValue(value);
                    //从环境中获取属性文件信息
                    //MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
                    //遍历所有的资源文件，寻找属性值
                    for (org.springframework.core.env.PropertySource<?> currentSource : propertySources) {
                        //这里就相当于从 map 里面取值，获取属性值
                        Object realValue = currentSource.getProperty(strValue);
                        if (realValue != null) {
                            System.out.println("realValue:" + realValue);
                            value = realValue;
                        }
                    }
                }
            } else {
                //这个样例项目，我只配置了@Value 和 @Autowired ，所以这里就是需要注入 bean 属性了。
                ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
                //获取属性类型，即该属性 bean 是哪个类型的
                Class<?> dependencyType = desc.getDependencyType();
                //在容器里查询该类型的 bean，因为同一个类型，可能实例化多个不同的 bean，所以用数组接收
                String[] candidateBeans = beanFactory.getBeanNamesForType(dependencyType);
                System.out.println(Arrays.toString(candidateBeans));
                //这里我们简化一下，假设只有一个候选 bean，当然实际情况确实会有多个，如果有多个的化，就获取@Qualifier指定优先获取的那个
                String beanName = candidateBeans[0];
                //获取到bean名称之后，就可以调用容器的getBean方法，来创建bean实例
                Object beanObj = beanFactory.getBean(beanName);
                value = beanObj;
            }

            //属性赋值
            if (value != null) {
                ReflectionUtils.makeAccessible(field);
                field.set(propertyBean, value);
            }
        }

        System.out.println(propertyBean);
        //至此。属性赋值过程结束。属性也都赋值成功了。
    }

    //获取 @Value 的值，简化版
    private static String resolveEmbeddedValue(Object value) {
        String newValue = (String) value;
        newValue = newValue.replace("${", "");
        newValue = newValue.replace("}", "");
        return newValue;
    }


    //判断属性上面是否标注了 @Autowired, @Value 或 @Inject
    private static MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
        //把三种类型放入列表中
        Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);
        autowiredAnnotationTypes.add(Autowired.class);
        autowiredAnnotationTypes.add(Value.class);
        try {
            autowiredAnnotationTypes.add((Class<? extends Annotation>)
                    ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
            logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
        }
        catch (ClassNotFoundException ex) {
            logger.info(ex);
            // JSR-330 API not available - simply skip.
        }

        //获取属性上的所有注解
        MergedAnnotations annotations = MergedAnnotations.from(ao);
        //遍历刚刚填入三种了类型的列表。看看属性的注解是否包含这三个属性之一
        for (Class<? extends Annotation> type : autowiredAnnotationTypes) {
            MergedAnnotation<?> annotation = annotations.get(type);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        return null;
    }

}
