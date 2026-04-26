package dev.vinciguerra.adrsentinel.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST adibito esclusivamente al monitoraggio dello stato di salute dell'applicazione (Health Check / Liveness Probe).
 * <p>
 * <b>Ruolo Architetturale (Observability & Monitoring):</b><br>
 * Questo controller espone un endpoint ultraleggero, intenzionalmente privo di logica di business complessa 
 * e di connessioni al database. È progettato per rispondere nel minor tempo possibile (bassa latenza) ed è 
 * il punto di aggancio vitale per le infrastrutture di rete e di orchestrazione esterne:
 * </p>
 * <ul>
 * <li><b>Load Balancers (es. AWS ALB, NGINX):</b> Lo interrogano periodicamente per decidere se il nodo 
 * è sano e può ricevere traffico HTTP in ingresso.</li>
 * <li><b>Orchestratori (es. Kubernetes, Docker Swarm):</b> Lo utilizzano come <i>Liveness Probe</i> per 
 * riavviare automaticamente il container in caso di stallo (deadlock), o come <i>Readiness Probe</i> per 
 * gestire le strategie di Zero-Downtime Deployment.</li>
 * <li><b>Sistemi di Alerting (es. Datadog, UptimeRobot):</b> Lo sfruttano per misurare i Service Level Agreement (SLA) 
 * calcolando l'effettiva percentuale di uptime del server.</li>
 * </ul>
 * <p>
 * <i>Nota di Sicurezza:</i> A differenza degli endpoint di business protetti da Spring Security o dei 
 * moduli avanzati come Spring Boot Actuator ({@code /actuator/health}), questa rotta viene tipicamente 
 * mantenuta pubblica (o inserita in whitelist) per consentire ping veloci e frequenti dall'infrastruttura di rete.
 * </p>
 *
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/adr-sentinel/ping")
public class PingController {
	/**
	 * Firma testuale di risposta per l'Health Check.
	 * <p>
	 * <b>Micro-ottimizzazione della Memoria:</b> L'estrazione in una costante {@code static final} 
	 * garantisce che la stringa venga allocata una singola volta nella String Pool della JVM 
	 * al caricamento della classe. Poiché questo endpoint può subire migliaia di interrogazioni 
	 * al minuto dai sistemi di monitoraggio, si evitano inutili allocazioni di memoria per richiesta, 
	 * abbattendo il carico sul Garbage Collector.
	 * </p>
	 */
	private static final String PING_RESPONSE = "Server ADR Sentinel: STATUS ONLINE\n";
	
	/**
	 * Gestisce le richieste HTTP GET in arrivo sulla rotta {@code /ping}.
	 * <p>
	 * Risponde immediatamente per confermare che l'Application Context di Spring è stato inizializzato 
	 * correttamente e che il web server sottostante è in ascolto. Spring restituirà automaticamente 
	 * un HTTP Status Code <b>200 OK</b>.
	 * </p>
	 * <p>
	 * <b>Scelta Architetturale sulle Performance:</b><br>
	 * Il metodo restituisce la costante in plain-text anziché un oggetto mappato in JSON, 
	 * bypassando l'engine di serializzazione di Jackson per mantenere l'overhead computazionale 
	 * rigorosamente a zero.
	 * </p>
	 *
	 * @return la costante {@link #PING_RESPONSE} che attesta l'operatività del nodo.
	 */
	@GetMapping
	public  String ping() {
		return PING_RESPONSE;
	}
}
