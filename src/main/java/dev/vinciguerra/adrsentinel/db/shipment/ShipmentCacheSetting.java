package dev.vinciguerra.adrsentinel.db.shipment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mappatore tipizzato (Type-Safe Configuration Properties) per il binding dinamico 
 * delle policy di limitazione della memoria (Capacity Planning) per l'entità Shipment.
 * <p>
 * <b>Architettura e Principio della Twelve-Factor App:</b><br>
 * Questa classe agisce da ponte inalterabile tra la configurazione esternalizzata (il file {@code application.yml}) 
 * e l'infrastruttura di memoria interna (il CacheManager di Spring/Caffeine). 
 * Estraendo i valori dal nodo root {@code adr-sentinel.cache.shipment}, permette di dimensionare 
 * le cache senza necessitare di ricompilazioni del codice sorgente.
 * </p>
 * <p>
 * <b>Thread-Safety e Immutabilità:</b><br>
 * L'utilizzo del costrutto {@code record} garantisce che la configurazione sia "scolpita nella pietra" 
 * fin dal momento del boot applicativo. Nessun servizio o componente di business possiede metodi setter 
 * in grado di sovrascrivere o corrompere i limiti di RAM a runtime.
 * </p>
 *
 * @param tracking la policy di cache applicata alle ricerche puntuali (1-to-1) basate sul Tracking Number. 
 * Generalmente dimensionata per gestire alti volumi di chiavi singole ad altissima rotazione.
 * @param period la policy di cache applicata alle interrogazioni raggruppate per intervalli temporali (es. singole giornate). 
 * Ottimizzata per conservare in memoria i piani di carico operativi correnti.
 * @param vehicle la policy di cache dedicata allo storico delle spedizioni aggregate per singolo mezzo della flotta.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Shipment Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.shipment")
public record ShipmentCacheSetting(CachePolicy tracking, CachePolicy period, CachePolicy vehicle) {
	/**
	 * Struttura dati contrattuale che definisce i vincoli fisici di una specifica regione di cache.
	 * <p>
	 * <b>Relaxed Binding:</b><br>
	 * Grazie al motore di property binding di Spring Boot, la variabile in notazione camelCase 
	 * ({@code maxSize}) intercetta e mappa automaticamente la chiave scritta in kebab-case 
	 * ({@code max-size}) all'interno del file YAML.
	 * </p>
	 * @param maxSize la capienza massima (Upper Bound) della cache. Rappresenta il numero limite 
	 * di chiavi memorizzabili prima che il motore (es. Caffeine) inneschi l'algoritmo 
	 * di espulsione (Eviction) per liberare spazio nella Heap della JVM.
	 */
	public record CachePolicy(int maxSize) {}
}
