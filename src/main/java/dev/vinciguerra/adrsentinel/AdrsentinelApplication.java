package dev.vinciguerra.adrsentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Punto di ingresso principale dell'applicazione backend Adrsentinel.
 * <p>
 * Questa classe ha la responsabilità di avviare l'Inversion of Control (IoC) container di Spring,
 * eseguire lo scan dei componenti, configurare le dipendenze automatiche e avviare il web server integrato.
 * </p>
 * <h3>Dettagli Architetturali:</h3>
 * <ul>
 * <li>{@code @SpringBootApplication}: Abilita l'auto-configurazione di Spring Data JPA, Web MVC e la scansione dei Bean.</li>
 * <li>{@code @EnableCaching}: <b>Cruciale per le performance.</b> Attiva il proxy interceptor per la gestione
 * della cache in memoria. Permette l'iniezione del {@code CacheManager} nei Service (es. AdrClass, UnNumber) 
 * per ridurre drasticamente le query di lettura (I/O) sul database PostgreSQL.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableCaching
public class AdrsentinelApplication {
	/**
     * Metodo main standard di Java che delega l'avvio del ciclo di vita dell'applicazione a Spring Boot.
     *
     * @param args Argomenti opzionali passati da riga di comando durante l'avvio del file .jar.
     * Possono essere utilizzati per sovrascrivere le proprietà di application.yml.
     */
	public static void main(String[] args) {
		SpringApplication.run(AdrsentinelApplication.class, args);
	}
}
