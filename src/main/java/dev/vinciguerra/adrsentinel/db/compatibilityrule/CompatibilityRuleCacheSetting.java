package dev.vinciguerra.adrsentinel.db.compatibilityrule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mappatore tipizzato (Type-Safe Configuration Properties) per il binding dinamico 
 * delle policy di limitazione della memoria dedicate alle Regole di Compatibilità ADR 
 * (Matrice di Segregazione).
 * <p>
 * <b>Dominio Logistico (Segregation Matrix):</b><br>
 * Nel trasporto di merci pericolose, la Matrice di Segregazione determina contrattualmente 
 * se due materie con classi di pericolo differenti (es. Esplosivi e Corrosivi) possono essere 
 * caricate a bordo dello stesso veicolo. Questa classe configura la memoria per l'estrazione 
 * ultrarapida di tali regole.
 * </p>
 * <p>
 * <b>Architettura e Capacity Planning:</b><br>
 * Essendo le classi ADR principali un numero strettamente chiuso (circa 9), il calcolo 
 * combinatorio delle regole di compatibilità genera un volume di dati microscopico e statico. 
 * L'utilizzo di questo {@code record} immutabile garantisce che i limiti (Upper Bounds) 
 * letti dal file {@code application.yml} al boot dell'applicazione non possano essere 
 * manomessi a runtime.
 * </p>
 * @param adrClassA la policy di cache applicata all'estrazione di tutte le regole di 
 * compatibilità partendo da una specifica classe "Sorgente" (es. "Quali classi posso 
 * caricare insieme alla Classe 3?"). Considerato il numero limitato di classi, il limite 
 * di questa cache può essere impostato a un valore estremamente conservativo (es. 15).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Segregation Matrix Caching)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.compatibilityrule")
public record CompatibilityRuleCacheSetting(CachePolicy adrClassA) {
	/**
	 * Struttura dati contrattuale che definisce i vincoli fisici di una specifica regione di cache.
	 * <p>
	 * <b>Relaxed Binding:</b><br>
	 * Grazie all'infrastruttura di Spring Boot, la proprietà in notazione camelCase 
	 * ({@code maxSize}) intercetta e deserializza autonomamente la chiave scritta in 
	 * kebab-case ({@code max-size}) presente nel blocco YAML.
	 * </p>
	 * @param maxSize la capienza massima della regione di memoria dedicata a queste regole.
	 * Oltrepassata questa soglia, interviene l'algoritmo di espulsione di Caffeine.
	 */
	public record CachePolicy(int maxSize) {}
}
