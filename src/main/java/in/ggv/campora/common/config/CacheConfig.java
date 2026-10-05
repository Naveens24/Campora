package in.ggv.campora.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory caching for slow-changing reference data (stops, departments).
 * Every form on the site renders these lists, so caching removes the most
 * repeated query in the app. Admin edits evict the cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String LOCATIONS = "locations";
    public static final String DEPARTMENTS = "departments";
}
