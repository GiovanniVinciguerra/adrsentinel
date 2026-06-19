package dev.vinciguerra.adrsentinel.db.driver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Componente di configurazione immutabile (Java Record) deputato alla mappatura, centralizzazione
 * e validazione delle proprietà esterne relative alle strategie di caching dell'entità Driver.
 * <p>
 * L'annotazione {@link ConfigurationProperties} istruisce il meccanismo di externalized configuration 
 * di Spring Boot a eseguire un binding gerarchico e fortemente tipizzato dei valori definiti nei file 
 * sorgente (es. {@code application.yml} o {@code application.properties}), ancorandosi al prefisso 
 * radice {@code adr-sentinel.cache.driver}.
 * </p>
 * <p>
 * L'adozione del costrutto {@code record} (introdotto stabilmente in Java 16) garantisce intrinsecamente 
 * l'immutabilità thread-safe di queste impostazioni una volta completata la fase di bootstrap 
 * dell'applicazione, inibendo qualsiasi tentativo di alterazione a runtime (immutabilità dei metadati).
 * </p>
 * @param license Configurazione contrattuale dedicata alla regione di cache che gestisce le interrogazioni 
 * puntuali dei singoli conducenti tramite la chiave di licenza/patente. Mappa la sotto-struttura 
 * presente sotto il percorso {@code adr-sentinel.cache.driver.license}.
 * @param allDriver Configurazione contrattuale dedicata alla regione di cache che memorizza i dataset collettivi, 
 * come l'elenco massivo di tutti i driver censiti. Sfrutta le regole di <i>Relaxed Binding</i> 
 * per mappare in modo trasparente la chiave kebab-case {@code all-driver} definita nel file YAML.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe AdrClass Caching Infrastructure)
 * @since 3.0
 */
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
