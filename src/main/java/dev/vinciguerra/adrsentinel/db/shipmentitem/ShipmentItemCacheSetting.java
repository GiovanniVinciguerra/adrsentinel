package dev.vinciguerra.adrsentinel.db.shipmentitem;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Record di configurazione type-safe per il tuning dinamico delle cache relative all'entità ShipmentItem.
 * <p>
 * <b>Architettura (Externalized Configuration & Immutabilità):</b><br>
 * Questa classe funge da ponte (binding) tra le configurazioni esternalizzate (es. {@code application.yml}) 
 * e il sistema di caching in Java. Sfruttando la natura immutabile dei <b>Java Record</b>, Spring Boot 
 * inietta questi valori all'avvio dell'applicazione tramite il costruttore implicito, rendendo le 
 * impostazioni blindate (Thread-Safe) per tutta la durata del ciclo di vita dell'applicazione.
 * </p>
 * <p>
 * <b>Integrazione YAML (Esempio d'uso):</b><br>
 * Per valorizzare questo record, il file di configurazione dovrà rispecchiare il prefisso dichiarato 
 * e la gerarchia dei parametri in questo modo:
 * <pre>
 * adr-sentinel:
 *   cache:
 *     shipment-item:
 *       item-uuid:
 *         max-size: 5000
 *       shipment:
 *         max-size: 100
 * </pre>
 * </p>
 * @param itemUUID policy di configurazione dedicate alla cache di accesso puntuale (chiave: String UUID).
 * @param shipment policy di configurazione dedicate alla cache di aggregazione collezionale 
 * (chiave: String Tracking Number, valore: List).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Cache Settings)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.shipmentitem")
public record ShipmentItemCacheSetting(CachePolicy itemUUID, CachePolicy shipment) {
	/**
	 * Sotto-struttura riutilizzabile che definisce le metriche operative e i limiti di una 
	 * singola topologia di cache.
	 * <p>
	 * <b>Design Pattern:</b><br>
	 * Isolare queste proprietà in un sub-record dedicato permette di scalare facilmente la 
	 * configurazione in futuro (es. aggiungendo policy come {@code expireAfterWrite} o 
	 * {@code expireAfterAccess}) mantenendo la gerarchia del file YAML pulita e standardizzata 
	 * per tutte le cache del sistema.
	 * </p>
	 * @param maxSize definisce l'hard-limit capacitivo della cache (Size-Based Eviction). 
	 * Raggiunto questo valore, il provider di cache (es. Caffeine tramite 
	 * algoritmo Window TinyLFU) inizierà a sfrattare le entry meno rilevanti 
	 * per prevenire saturazioni di memoria ({@code OutOfMemoryError}).
	 */
	public record CachePolicy(int maxSize) {}
}
