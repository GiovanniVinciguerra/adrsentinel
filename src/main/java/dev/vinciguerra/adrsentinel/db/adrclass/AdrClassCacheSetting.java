package dev.vinciguerra.adrsentinel.db.adrclass;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mappatore tipizzato (Type-Safe Configuration Properties) per il binding dinamico 
 * delle policy di limitazione della memoria dedicate all'entità {@code AdrClass}.
 * <p>
 * <b>Architettura e Principio della Twelve-Factor App:</b><br>
 * Questa classe separa  l'infrastruttura di memoria dal codice sorgente. Leggendo i 
 * parametri dal nodo {@code adr-sentinel.cache.adrclass} del file YAML, permette ai 
 * sistemisti di dimensionare le cache senza dover ricompilare o riavviare forzatamente 
 * l'applicativo.
 * </p>
 * <p>
 * <b>Thread-Safety e Immutabilità:</b><br>
 * L'implementazione tramite costrutto {@code record} nativo di Java assicura che 
 * le regole di Capacity Planning siano di sola lettura (Read-Only) dal momento del boot, 
 * impedendo qualsiasi alterazione accidentale a runtime da parte della Business Logic.
 * </p>
 * @param classCode la policy di cache applicata alle ricerche puntuali (1-to-1) basate 
 * sul codice univoco della classe (es. "3" per i Liquidi Infiammabili). Dato il numero 
 * chiuso di classi ADR, questo limite sarà strutturalmente molto basso.
 * @param allAdr la policy di cache dedicata al raggruppamento globale (il catalogo intero 
 * delle classi). Essendo un singolo elemento di lista, il limite può essere impostato 
 * a valori minimi (es. 1).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe AdrClass Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.adrclass")
public record AdrClassCacheSetting(CachePolicy classCode, CachePolicy allAdr) {
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
