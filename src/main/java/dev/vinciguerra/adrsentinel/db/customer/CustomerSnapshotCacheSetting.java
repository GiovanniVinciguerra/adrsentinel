package dev.vinciguerra.adrsentinel.db.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Struttura dati immutabile (Configuration Properties) delegata al binding tipizzato delle direttive di 
 * configurazione per la cache dei reperti storici (CustomerSnapshot).
 * <p><b>Ruolo Architetturale e Type-Safe Configuration:</b></p>
 * Questa classe sfrutta il motore di {@code @ConfigurationProperties} di Spring Boot per mappare in 
 * modo strutturato, validato e strongly-typed le proprietà fisiche definite nei file d'ambiente 
 * (es. {@code application.yml}). Utilizzando un costrutto {@code record}, l'infrastruttura garantisce che le 
 * policy di dimensionamento della memoria (impostate al bootstrap dell'applicazione) rimangano rigorosamente 
 * immutabili e thread-safe durante l'intero ciclo di vita del server, prevenendo alterazioni o corruzioni 
 * accidentali a runtime.
 * <p><b>Gerarchia del prefisso (Isolamento del Namespace):</b></p>
 * Il prefisso {@code adr-sentinel.cache.customer-snapshot} isola logicamente le impostazioni di questa specifica 
 * regione di memoria dal resto del configuration tree, aderendo al principio di Singola Responsabilità (SRP) anche 
 * a livello di deployment e agevolando l'onboarding operativo (DevOps).
 * @param shipmentId La policy di dimensionamento fisico associata alla regione di cache indicizzata per ID spedizione. 
 * Tramite il meccanismo di Relaxed Binding del framework, questa variabile intercetta automaticamente la chiave YAML 
 * configurata come {@code adr-sentinel.cache.customer-snapshot.shipment-id}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Fleet Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.customer-snapshot")
public record CustomerSnapshotCacheSetting(CachePolicy shipmentId) {
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
