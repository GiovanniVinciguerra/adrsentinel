package dev.vinciguerra.adrsentinel.web.component;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro di sicurezza perimetrale (Edge Security Filter) progettato per mitigare 
 * tentativi di attacco Denial of Service (DoS) basati sull'esaurimento delle risorse (RAM/Bandwidth).
 * <p>
 * <b>Ruolo Architetturale (Scudo di Primo Livello):</b><br>
 * Questo componente intercetta il traffico HTTP in ingresso prima ancora che raggiunga il 
 * layer di deserializzazione JSON (Jackson) o i Controller REST. Agisce analizzando 
 * l'intestazione {@code Content-Length} della richiesta. Se la dimensione dichiarata supera 
 * la soglia massima consentita, la richiesta viene abortita istantaneamente (Fail-Fast), 
 * risparmiando cicli di CPU e allocazioni di memoria (Heap) potenzialmente fatali per la JVM.
 * </p>
 * <p>
 * <b>Compliance e Standard (RFC 9110):</b><br>
 * In caso di violazione della soglia, il filtro risponde con lo status code HTTP 413 
 * ({@link HttpStatus#CONTENT_TOO_LARGE}), allineandosi alle più recenti direttive IETF, 
 * accompagnato da un payload JSON standardizzato per i client consumatori.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Security Hardened)
 * @since 3.0
 */
@Component
public class MaxPayLoadSizeComponent extends OncePerRequestFilter {
	/**
	 * Limite massimo tollerato per il payload di una singola richiesta HTTP.
	 * Impostato a 32 Kilobytes (32 * 1024 bytes). Questo valore è frutto di un rigoroso 
	 * Capacity Planning sulle dimensioni massime (Worst-Case Scenario) dei DTO in ingresso.
	 */
	private static final long MAX_PAYLOAD_BYTES = 32 * 1024;
	/** Logger dinamico dedicato al tracciamento degli eventi di sicurezza di questo componente. */
	private final Logger logger = LoggerFactory.getLogger(MaxPayLoadSizeComponent.class);
	
	/**
	 * Intercetta e ispeziona ogni singola richiesta HTTP per valutarne l'impatto dimensionale.
	 * <p>
	 * <b>Flusso di Esecuzione:</b>
	 * <ol>
	 * <li>Estrae la lunghezza dichiarata del payload dalla richiesta HTTP.</li>
	 * <li>Confronta il valore con la soglia di sicurezza ({@value #MAX_PAYLOAD_BYTES} bytes).</li>
	 * <li>Se il limite viene superato, interrompe la catena dei filtri (Filter Chain), 
	 * logga il tentativo di violazione a livello WARN e restituisce istantaneamente una risposta HTTP 413 in formato JSON.</li>
	 * <li>Se il limite è rispettato, delega in modo trasparente l'elaborazione al filtro successivo.</li>
	 * </ol>
	 * </p>
	 * @param request la richiesta HTTP in ingresso intercettata dal Servlet Container.
	 * @param response la risposta HTTP in uscita, utilizzata per inviare l'errore 413 in caso di blocco.
	 * @param filterChain la catena dei filtri di Spring Security/Web; invocata solo se la validazione dimensionale ha successo.
	 * @throws ServletException se si verifica un'eccezione a livello di framework Servlet durante l'elaborazione.
	 * @throws IOException se si verifica un errore di I/O, tipicamente durante la scrittura forzata del payload di errore.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long contentLength = request.getContentLengthLong();
		if(contentLength > MAX_PAYLOAD_BYTES) {
			logger.warn("Security Alert: Incoming HTTP request rejected. Payload size exceeded the maximum permitted limit (32KB). Potential DoS or misconfiguration issue.");
			response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
			response.setContentType("application/json");
			response.getWriter().write("{\"error\": \"Malformed payload: request size exceeds the 2MB limit.\"}");
		} else
			filterChain.doFilter(request, response);
	}
}
