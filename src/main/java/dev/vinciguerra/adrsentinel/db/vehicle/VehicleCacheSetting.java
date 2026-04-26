package dev.vinciguerra.adrsentinel.db.vehicle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mappatore tipizzato (Type-Safe Configuration Properties) per il binding dinamico 
 * delle policy di limitazione della memoria dedicate all'entità {@code Vehicle} (Flotta Aziendale).
 * <p>
 * <b>Dominio Logistico e Capacity Planning (Bounded Data):</b><br>
 * La flotta di veicoli di un'azienda rappresenta un set di dati limitato e a bassa frequenza di mutazione. 
 * Questo rende l'entità un candidato ideale per un caching aggressivo in RAM. Tramite questo record, 
 * i sistemisti possono dimensionare le diverse regioni di memoria (tramite il file {@code application.yml}) 
 * adattandole alle reali dimensioni del parco mezzi aziendale, garantendo latenza O(1) in fase di 
 * pianificazione dei trasporti.
 * </p>
 * <p>
 * <b>Immutabilità Architetturale:</b><br>
 * Il costrutto {@code record} garantisce che le configurazioni di memoria siano sigillate 
 * (Thread-Safe e Read-Only) al momento dell'avvio dell'applicazione. Non esistono metodi setter 
 * che possano alterare accidentalmente il Capacity Planning a runtime.
 * </p>
 * @param licensePlate la policy di cache per l'estrazione puntuale (1-to-1) di un singolo mezzo 
 * tramite la sua targa (chiave di business univoca). Fondamentale per i controlli operativi istantanei.
 * @param maxUsefulWeight la policy di cache per il raggruppamento (1-to-N) dei mezzi in base 
 * alla loro portata utile (Capacity). Ottimizzata per l'algoritmo di assegnazione del carico 
 * (es. "Cerca tutti i camion in grado di trasportare 24.000 kg").
 * @param allVehicle la policy di cache dedicata al raggruppamento globale dell'intera flotta. 
 * Utilizzata per alimentare istantaneamente le dashboard amministrative e le Select/Dropdown 
 * sul frontend React.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Type-Safe Fleet Caching Infrastructure)
 * @since 3.0
 */
@ConfigurationProperties(prefix = "adr-sentinel.cache.vehicle")
public record VehicleCacheSetting(CachePolicy licensePlate, CachePolicy maxUsefulWeight, CachePolicy allVehicle) {
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
