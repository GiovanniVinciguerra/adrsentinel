package dev.vinciguerra.adrsentinel.db.driver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Struttura dati tipizzata (Type-Safe Configuration) e intrinsecamente immutabile delegata 
 * al binding dei parametri infrastrutturali relativi alle cache del dominio {@link DriverSnapshot}.
 * <p><b>Contesto Architetturale (Configuration Binding):</b></p>
 * Sfruttando il motore {@code @ConfigurationProperties} di Spring Boot, questo {@code record} agisce 
 * come ponte contrattuale tra la configurazione esternata (es. {@code application.yml}) e l'infrastruttura Java. 
 * L'impiego dei record (introdotti in Java 14) garantisce che, una volta popolate all'avvio del contesto 
 * applicativo (Startup), le direttive di dimensionamento della cache siano <i>Thread-Safe</i> e 
 * non possano subire alterazioni (Mutazioni) a runtime, assicurando assoluta stabilità al layer di caching.
 * @param shimpmentId La policy di configurazione dedicata alla regione di cache che indicizza 
 * gli snapshot tramite l'identificativo della spedizione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Fleet Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.driver-snapshot")
public record DriverSnapshotCacheSetting(CachePolicy shipmentId) {
	/**
	 * Struttura dati contrattuale che definisce i vincoli fisici di una specifica regione di cache.
	 * <p>
	 * <b>Relaxed Binding:</b><br>
	 * Grazie al motore di property binding di Spring Boot, la proprietà camelCase 
	 * ({@code maxSize}) viene mappata in modo automatico e trasparente dalla rispettiva 
	 * chiave scritta in kebab-case ({@code max-size}) nel file YAML.
	 * </p>
	 * @param maxSize la capienza massima (Upper Bound) della regione di memoria. 
	 * Indica il numero massimo di chiavi tollerate prima che Caffeine inneschi l'algoritmo di Eviction.
	 */
	public record CachePolicy(int maxSize) {}
}
