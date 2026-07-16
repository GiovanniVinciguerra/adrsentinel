package dev.vinciguerra.adrsentinel.db.shipmentroute;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Componente architetturale di configurazione (Type-Safe Configuration Properties) 
 * deputato al binding dinamico dei parametri di caching definiti nell'ambiente esterno 
 * (es. file {@code application.yml} o variabili d'ambiente).
 * <p>
 * <b>Contesto Architetturale (Externalized Configuration):</b><br>
 * Questa classe elimina l'anti-pattern dei "Magic Numbers" (valori hardcoded nel codice) 
 * e l'uso frammentato dell'annotazione {@code @Value}. Raggruppa in modo coeso e gerarchico 
 * tutte le policy di ottimizzazione e dimensionamento (sizing) delle memorie cache 
 * legate alle rotte logistiche (Shipment Routes).
 * </p>
 * <p>
 * <b>Design Pattern (Immutable Configuration):</b><br>
 * L'utilizzo nativo della struttura {@code record} in combinazione con Spring Boot 
 * garantisce un'<b>Immutabilità Assoluta</b> (Thread-Safety) delle configurazioni a runtime. 
 * Una volta che il framework ha istanziato questo record durante la fase di Bootstrap 
 * dell'Application Context (tramite Constructor Binding implicito), i limiti della cache 
 * non potranno più essere alterati accidentalmente da nessun servizio interno, prevenendo 
 * bug di corruzione della memoria concorrente.
 * </p>
 * @param routeUUID L'oggetto contenente le policy (es. limiti di memoria) applicate in modo 
 * specifico alla regione di cache (SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE) denominata "routeUUID". 
 * Grazie al prefix di classe e al property binding, questo parametro mappa direttamente il ramo 
 * {@code adr-sentinel.cache.shipment-route.route-uuid} all'interno del file YAML.
 * @param shipmentTrackingNumber L'oggetto contenente le policy (es. limiti di memoria) applicate in modo 
 * specifico alla regione di cache (SHIPMENT_ROUTE_BY_SHIPMENT_CACHE) denominata "shipmentTrackingNumber". 
 * Grazie al prefix di classe e al property binding, questo parametro mappa direttamente il ramo 
 * {@code adr-sentinel.cache.shipment-route.shipment-tracking-number} all'interno del file YAML.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Shipment Route Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.shipmentroute")
public record ShipmentRouteCacheSetting(CachePolicy routeUUID, CachePolicy shipmentTrackingNumber) {
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
