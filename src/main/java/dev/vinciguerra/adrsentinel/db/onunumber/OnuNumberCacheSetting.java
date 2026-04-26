package dev.vinciguerra.adrsentinel.db.onunumber;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mappatore tipizzato (Type-Safe Configuration Properties) per il binding dinamico 
 * delle policy di limitazione della memoria dedicate al catalogo ADR (Numeri ONU).
 * <p>
 * <b>Architettura e Capacity Planning (Bounded Data):</b><br>
 * A differenza delle entità transazionali, il catalogo mondiale delle merci pericolose 
 * è un set di dati chiuso e limitato (circa 3.500 record). L'utilizzo di questa classe 
 * permette ai sistemisti di configurare i limiti di RAM in modo conservativo (es. 3000). Questo stratagemma 
 * emula una cache "infinita" (prevenendo l'evizione dei dati di catalogo) proteggendo al contempo 
 * la JVM da memory leak o anomalie del database.
 * </p>
 * <p>
 * <b>Immutabilità (Record Pattern):</b><br>
 * Essendo definita come {@code record}, l'intera configurazione risulta Thread-Safe e di 
 * sola lettura (Read-Only) dal momento dell'avvio dell'applicazione. Nessun componente 
 * di business può alterare le regole di caching a runtime.
 * </p>
 * @param onuCode la policy di cache per l'estrazione puntuale (1-to-1) di una specifica materia 
 * tramite il suo codice a 4 cifre.
 * @param kemlerCode la policy di cache per il raggruppamento (1-to-N) delle materie in base 
 * al loro codice identificativo di pericolo (es. raggruppare i liquidi infiammabili).
 * @param adrClass la policy di cache per il raggruppamento (1-to-N) delle materie appartenenti 
 * alla stessa macro-classe ADR.
 * @param allOnuNumber la policy di cache per la conservazione dell'intero catalogo globale. 
 * Poiché questa cache ospita un'unica e grande stringa/lista, il limite massimo può essere 
 * impostato a valori molto bassi (es. 10) garantendo comunque la conservazione dell'intero payload.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe OnuNumber Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.onunumber")
public record OnuNumberCacheSetting(CachePolicy onuCode, CachePolicy kemlerCode, CachePolicy adrClass, CachePolicy allOnuNumber) {
	/**
	 * Struttura dati contrattuale che definisce i vincoli fisici di una specifica regione di cache.
	 * <p>
	 * <b>Relaxed Binding:</b><br>
	 * Sfruttando il motore di property binding di Spring Boot, la variabile in notazione camelCase 
	 * ({@code maxSize}) si collega automaticamente e in modo trasparente alla chiave scritta in 
	 * kebab-case ({@code max-size}) presente nel file YAML.
	 * </p>
	 * @param maxSize la capienza massima (Upper Bound) della regione di memoria. 
	 * Valori estremamente alti disattivano virtualmente l'algoritmo di espulsione spaziale (Eviction).
	 */
	public record CachePolicy(int maxSize) {}
}
