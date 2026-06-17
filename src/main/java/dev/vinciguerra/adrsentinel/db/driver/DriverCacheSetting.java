package dev.vinciguerra.adrsentinel.db.driver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adr-sentinel.cache.driver")
public record DriverCacheSetting(CachePolicy license, CachePolicy allDriver) {
	/**
	 * Struttura dati contrattuale che definisce i vincoli fisici di una specifica regione di cache.
	 * <p>
	 * <b>Relaxed Binding:</b><br>
	 * Il framework Spring Boot mappa automaticamente la variabile camelCase {@code maxSize} 
	 * con la chiave kebab-case {@code max-size} presente nel file {@code application.yml}.
	 * </p>
	 * @param maxSize la capienza massima (Upper Bound) della regione di memoria. 
	 * Indica il numero limite di chiavi conservabili prima dell'intervento dell'algoritmo 
	 * di espulsione (Eviction).
	 */
	public record CachePolicy(int maxSize) {}
}
