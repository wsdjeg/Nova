package net.wsdjeg.nova;

import java.util.List;

/**
 * AI 服务商信息
 */
public class Provider {
    public String name;
    public List<String> models;
    
    public Provider(String name, List<String> models) {
        this.name = name;
        this.models = models;
    }
}

