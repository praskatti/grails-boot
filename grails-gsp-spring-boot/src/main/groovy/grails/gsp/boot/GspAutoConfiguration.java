/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.gsp.boot;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine;
import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateRenderer;
import org.codehaus.groovy.grails.web.pages.StandaloneTagLibraryLookup;
import org.codehaus.groovy.grails.web.pages.discovery.CachingGrailsConventionGroovyPageLocator;
import org.codehaus.groovy.grails.web.pages.discovery.GrailsConventionGroovyPageLocator;
import org.codehaus.groovy.grails.web.pages.discovery.GroovyPageLocator;
import org.codehaus.groovy.grails.web.servlet.view.GrailsLayoutViewResolver;
import org.codehaus.groovy.grails.web.servlet.view.GroovyPageViewResolver;
import org.codehaus.groovy.grails.web.sitemesh.GroovyPageLayoutFinder;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ViewResolver;

@Configuration
@ConditionalOnClass(GroovyPagesTemplateEngine.class)
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class GspAutoConfiguration {
    protected static abstract class AbstractGspConfig {
        @Value("${spring.gsp.reloadingEnabled:true}")
        boolean gspReloadingEnabled;
        
        @Value("${spring.gsp.view.cacheTimeout:1000}")
        long viewCacheTimeout;
    }
    
    @Configuration
    @Import({TagLibraryLookupRegistrar.class})
    protected static class GspTemplateEngineAutoConfiguration extends AbstractGspConfig {
        @Value("${spring.gsp.templateRoots:classpath:/templates}")
        String[] templateRoots;
        
        @Value("${spring.gsp.locator.cacheTimeout:5000}")
        long locatorCacheTimeout;
        
        @Value("${spring.gsp.layout.caching:true}")
        boolean gspLayoutCaching;
        
        @Value("${spring.gsp.layout.default:main}")
        String defaultLayoutName;

        @Bean(autowire=Autowire.BY_NAME)
        @ConditionalOnMissingBean(name="groovyPagesTemplateEngine") 
        GroovyPagesTemplateEngine groovyPagesTemplateEngine() {
            GroovyPagesTemplateEngine templateEngine = new GroovyPagesTemplateEngine();
            templateEngine.setReloadEnabled(gspReloadingEnabled);
            return templateEngine;
        }
        
        @Bean(autowire=Autowire.BY_NAME)
        @ConditionalOnMissingBean(name="groovyPageLocator")
        GroovyPageLocator groovyPageLocator() {
            CachingGrailsConventionGroovyPageLocator pageLocator = new CachingGrailsConventionGroovyPageLocator() {
                protected List<String> resolveSearchPaths(String uri) {
                    List<String> paths=new ArrayList<String>(templateRoots.length);
                    for(String rootPath : templateRoots) {
                        paths.add(rootPath + StringUtils.cleanPath(uri));
                    }
                    return paths;
                }
            };
            pageLocator.setReloadEnabled(gspReloadingEnabled);
            pageLocator.setCacheTimeout(gspReloadingEnabled ? locatorCacheTimeout : -1);
            return pageLocator;
        }
        
        @Bean
        @ConditionalOnMissingBean(name = "groovyPageLayoutFinder")
        public GroovyPageLayoutFinder groovyPageLayoutFinder() {
            GroovyPageLayoutFinder groovyPageLayoutFinder = new GroovyPageLayoutFinder();
            groovyPageLayoutFinder.setGspReloadEnabled(gspReloadingEnabled);
            groovyPageLayoutFinder.setCacheEnabled(gspLayoutCaching);
            groovyPageLayoutFinder.setEnableNonGspViews(false);
            groovyPageLayoutFinder.setDefaultDecoratorName(defaultLayoutName);
            return groovyPageLayoutFinder;
        }
        
        @Bean(autowire=Autowire.BY_NAME)
        @ConditionalOnMissingBean(name = "groovyPagesTemplateRenderer")
        GroovyPagesTemplateRenderer groovyPagesTemplateRenderer() {
            GroovyPagesTemplateRenderer groovyPagesTemplateRenderer = new GroovyPagesTemplateRenderer();
            groovyPagesTemplateRenderer.setCacheEnabled(!gspReloadingEnabled);
            return groovyPagesTemplateRenderer;
        }
    }
    
    @Configuration
    protected static class GspViewResolverConfiguration extends AbstractGspConfig {
        @Autowired
        GroovyPagesTemplateEngine groovyPagesTemplateEngine;
        
        @Autowired
        GrailsConventionGroovyPageLocator groovyPageLocator;
        
        @Autowired
        GroovyPageLayoutFinder groovyPageLayoutFinder;
        
        @Bean
        @ConditionalOnMissingBean(name = "gspViewResolver")
        public GrailsLayoutViewResolver gspViewResolver() {
            return new GrailsLayoutViewResolver(innerGspViewResolver(), groovyPageLayoutFinder);
        }

        ViewResolver innerGspViewResolver() {
            GroovyPageViewResolver innerGspViewResolver = new GroovyPageViewResolver(groovyPagesTemplateEngine, groovyPageLocator);
            innerGspViewResolver.setAllowGrailsViewCaching(!gspReloadingEnabled || viewCacheTimeout != 0);
            innerGspViewResolver.setCacheTimeout(gspReloadingEnabled ? viewCacheTimeout : -1);
            return innerGspViewResolver;
        }
    }
    
    protected static class TagLibraryLookupRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if(!registry.containsBeanDefinition("gspTagLibraryLookup")) {
                GenericBeanDefinition beanDefinition = createBeanDefinition(StandaloneTagLibraryLookup.class);
                
                ManagedList<BeanDefinition> list = new ManagedList<BeanDefinition>();
                registerTagLibs(list);
                
                beanDefinition.getPropertyValues().addPropertyValue("tagLibInstances", list);
                
                registry.registerBeanDefinition("gspTagLibraryLookup", beanDefinition);
                registry.registerAlias("gspTagLibraryLookup", "tagLibraryLookup");
            }
        }

        protected void registerTagLibs(ManagedList<BeanDefinition> list) {
            for(Class<?> taglibClazz : StandaloneTagLibraryLookup.DEFAULT_TAGLIB_CLASSES) {
                list.add(createBeanDefinition(taglibClazz));
            }
        }

        protected GenericBeanDefinition createBeanDefinition(Class<?> beanClass) {
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(beanClass);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_NAME);
            return beanDefinition;
        }
    }
}