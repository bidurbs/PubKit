/* Copyright (c) 2015 32skills Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.roquito;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.stereotype.Component;

/**
 * Created by puran
 */
@Component
public class RoquitoConfig {
    
    @Value("${app.environment}")
    private String environment;
    
    @Value("${redisdb.host}")
    private String redisHost;
    
    @Value("${redisdb.port}")
    private int redisPort;
    
    @Value("${redisdb.timeout}")
    private int redisTimeout;
    
    @Value("${redisdb.database}")
    private int redisDatabase;
    
    @Value("${redisdb.password}")
    private String redisPassword;
    
    @Value("${mongodb.host}")
    private String mongoHost;
    
    @Value("${mongodb.port}")
    private int mongoPort;
    
    @Value("${mongodb.database}")
    private String mongoDatabase;
    
    @Value("${mapdb.filepath}")
    private String mapdbFilePath;
    
    @Value("${mapdb.encryptedPassword}")
    private String mapdbPassword;
    
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public boolean isDevEnvironment() {
        return "dev".equalsIgnoreCase(environment);
    }
    
    public String getRedisHost() {
        return redisHost;
    }
    
    public int getRedisPort() {
        return redisPort;
    }
    
    public int getRedisTimeout() {
        return redisTimeout;
    }
    
    public int getRedisDatabase() {
        return redisDatabase;
    }
    
    public String getRedisPassword() {
        return redisPassword;
    }
    
    public String getMongoHost() {
        return mongoHost;
    }
    
    public int getMongoPort() {
        return mongoPort;
    }
    
    public String getMongoDatabase() {
        return mongoDatabase;
    }

    public String getMapdbFilePath() {
        return mapdbFilePath;
    }

    public void setMapdbFilePath(String mapdbFilePath) {
        this.mapdbFilePath = mapdbFilePath;
    }

    public String getMapdbPassword() {
        return mapdbPassword;
    }

    public void setMapdbPassword(String mapdbPassword) {
        this.mapdbPassword = mapdbPassword;
    }
}
