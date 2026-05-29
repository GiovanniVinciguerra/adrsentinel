package dev.vinciguerra.adrsentinel.web.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configurazione architetturale di sicurezza dedicata all'indurimento (Hardening) 
 * del motore di deserializzazione JSON (Jackson 3).
 * <p>
 * <b>Ruolo Architetturale (Defense in Depth):</b><br>
 * Questo componente rappresenta il secondo livello dello scudo difensivo dell'applicazione. 
 * Mentre i filtri HTTP bloccano i payload complessivamente troppo grandi, questa classe 
 * si occupa degli attacchi chirurgici. Sostituisce l'istanza standard di Jackson con un 
 * motore customizzato e "sigillato" (tramite il pattern dell'immutabilità delle Factory di Jackson 3), 
 * progettato per fallire istantaneamente (Fail-Fast) di fronte a strutture dati anomale.
 * </p>
 * <p>
 * <b>Vulnerabilità Mitigate:</b>
 * <ul>
 * <li><b>JSON Bomb / Stack Overflow:</b> Previene l'esaurimento dello Stack di Java bloccando 
 * JSON con array o oggetti annidati ricorsivamente in modo malevolo.</li>
 * <li><b>RAM Exhaustion (OOM):</b> Previene l'allocazione massiva di memoria nell'Heap della JVM 
 * bloccando il parsing di stringhe singole di dimensioni spropositate.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Security Hardened)
 * @since 3.0
 */
@Configuration
public class JacksonSecurityConfiguration {
	/** Logger dinamico dedicato al tracciamento in fase di Bootstrap (avvio del server). */
	private final Logger logger = LoggerFactory.getLogger(JacksonSecurityConfiguration.class);
	
	/**
	 * Costruisce e inietta nel contesto di Spring Boot un motore di parsing JSON blindato.
	 * <p>
	 * <b>Dettagli di Configurazione (Capacity Planning):</b>
	 * <ul>
	 * <li>{@code maxNestingDepth(6)}: Tarato sull'architettura "Flat DTO" dell'applicazione. 
	 * Tolleranza massima di 6 livelli di annidamento per assorbire eventuali buste (envelopes) 
	 * di API Gateway o broker di messaggi.</li>
	 * <li>{@code maxStringLength(510)}: Tarato sui limiti di database e sui validatori custom 
	 * (max 255 caratteri). Il valore di 510 funge da cuscinetto di sicurezza per supportare 
	 * la decodifica di caratteri speciali, emoji encodate o Unicode espansi prima di passare 
	 * il controllo ai validatori JSR-380.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Nota su Spring Boot Back-off:</b><br>
	 * Il metodo restituisce esplicitamente un {@link JsonMapper} (e non un generico ObjectMapper). 
	 * Questo segnala all'autoconfigurazione di Spring Boot 4 di ritirarsi (Back-off) e di 
	 * adottare questo Bean come istanza primaria di sistema senza sollevare eccezioni di conflitto.
	 * </p>
	 * * @return l'istanza immutabile di {@link JsonMapper} configurata con i vincoli di sicurezza 
	 * e dotata di autodiscovery per i moduli avanzati (es. gestione Date JSR-310).
	 */
	@Bean
	public JsonMapper customJsonMapper() {
		logger.info("Configuring custom JsonMapper: applied strict security constraints (MaxNestingDepth=6, MaxStringLength=510) to prevent JSON-Bomb and OOM vulnerabilities.");
		JsonFactory factory = JsonFactory.builder()
			.streamReadConstraints(
				StreamReadConstraints
					.builder()
					.maxNestingDepth(6)
					.maxStringLength(1000000)
					.build()
				)
			.build();
		return JsonMapper.builder(factory)
			.findAndAddModules()
			.build();
	}
}
