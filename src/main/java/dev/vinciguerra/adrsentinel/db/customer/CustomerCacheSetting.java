package dev.vinciguerra.adrsentinel.db.customer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Struttura dati immutabile preposta al mapping tipizzato delle configurazioni di caching 
 * (In-Memory Storage) per il dominio anagrafico {@code Customer}.
 * <p>
 * <b>Contesto Architetturale (Configuration Binding & Performance):</b><br>
 * Questa classe funge da ponte tra le direttive infrastrutturali esternalizzate (es. {@code application.yml}) 
 * e il layer applicativo. Sfruttando il <i>Configuration Properties Binding</i> di Spring Boot, 
 * converte property testuali in un grafo di oggetti Java fortemente tipizzati. Questo approccio 
 * fail-fast garantisce che configurazioni errate blocchino l'avvio dell'applicazione, prevenendo 
 * anomalie silenziose a runtime.
 * </p>
 * <p>
 * <b>Topologia della Cache (Granularità):</b><br>
 * Il record implementa una strategia di partizionamento tattico della memoria. Invece di usare 
 * un unico spazio condiviso (che comporterebbe politiche di espulsione sub-ottimali), definisce 
 * tre regioni (<i>Cache Names</i>) isolate. Questo consente di dimensionare ogni area in base 
 * agli specifici pattern di accesso (Hit Ratio vs Memory Footprint) previsti dal sistema.
 * </p>
 * @param vatNumber La policy associata alla regione di cache dedicata ai lookup esatti tramite 
 * Partita IVA (Business Key). Trattandosi di operazioni ad altissima frequenza (es. validazione 
 * massiva durante il caricamento di una spedizione), questa regione richiede tipicamente un 
 * {@code maxSize} elevato per massimizzare l'Hit Ratio.
 * @param companyName La policy associata alla regione dedicata alle ricerche tramite Ragione Sociale. 
 * Spesso utilizzata per auto-completamenti lato frontend o filtri di ricerca secondari.
 * @param allCustomer La policy associata alla regione preposta alla conservazione di estrazioni 
 * massive (es. impaginazioni o liste globali). Poiché l'impronta in memoria (Memory Footprint) 
 * di intere collezioni è gravosa, il {@code maxSize} è tipicamente limitato a valori molto bassi 
 * per scongiurare rischi di <i>OutOfMemoryError (OOM)</i>.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe AdrClass Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.customer")
public record CustomerCacheSetting(CachePolicy vatNumber, CachePolicy companyName, CachePolicy allCustomer) {
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
