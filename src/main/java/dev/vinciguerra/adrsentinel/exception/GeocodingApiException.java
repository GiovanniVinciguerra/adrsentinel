package dev.vinciguerra.adrsentinel.exception;

/**
 * Eccezione tecnica (Infrastructure Exception) non controllata (Unchecked) sollevata 
 * in caso di fallimento della comunicazione HTTP con il provider cartografico esterno.
 * <p>
 * <b>Contesto Architetturale (Exception Translation Pattern):</b><br>
 * Questa classe agisce da "scudo" tra l'infrastruttura di rete e il Domain Layer. 
 * Intercetta le eccezioni a basso livello del framework (es. {@code RestClientException} 
 * di Spring o {@code IOException}) e le "traduce" in un'eccezione di dominio comprensibile. 
 * Questo disaccoppiamento garantisce che i livelli superiori dell'applicazione non debbano 
 * conoscere i dettagli della libreria HTTP utilizzata.
 * </p>
 * <p>
 * <b>Classificazione dell'Errore (System Failure):</b><br>
 * A differenza di {@link AddressNotResolvableException} (che indica un errore semantico 
 * nei dati forniti dall'utente), questa eccezione segnala un guasto infrastrutturale. 
 * Scenari tipici includono:
 * <ul>
 * <li><b>HTTP 401/403:</b> Problemi di autenticazione (API Key revocata, errata o scaduta).</li>
 * <li><b>HTTP 429:</b> Superamento delle quote massime di chiamate consentite (Rate Limiting).</li>
 * <li><b>HTTP 500/503:</b> Il server del provider esterno è temporaneamente offline o in manutenzione.</li>
 * <li><b>I/O Timeout:</b> Impossibilità di stabilire l'handshake TCP/TLS con il server remoto.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Linee Guida di Sicurezza (Audit & Information Leakage):</b><br>
 * L'oggetto {@code cause} (Root Cause) contiene dettagli profondi sull'infrastruttura di rete 
 * (es. URL esatti, porte, frammenti di header). È fondamentale tracciare queste informazioni 
 * nei log interni (ELK, file di log) per l'analisi post-mortem, ma si deve intercettare 
 * questa eccezione al livello del Controller (es. tramite {@code @ExceptionHandler}) per 
 * restituire al client web un generico e sicuro HTTP 500 (Internal Server Error) o HTTP 503 (Service Unavailable), 
 * evitando la potenziale divulgazione di informazioni sensibili (Information Leakage).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 * @see RuntimeException
 */
public class GeocodingApiException extends RuntimeException {
	/**
	 * Identificatore univoco per la serializzazione.
	 * Indispensabile per mantenere l'integrità dell'oggetto qualora l'eccezione dovesse 
	 * essere serializzata per viaggiare su reti RMI o sistemi di logging distribuiti.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Costruisce l'eccezione tecnica incapsulando sia un messaggio contestuale 
	 * che l'errore originario (Root Cause).
	 * <p>
	 * Iniettando il parametro {@code cause}, si preserva l'intero stacktrace originario 
	 * generato dal framework HTTP, garantendo agli sviluppatori una tracciabilità 
	 * (Traceability) profonda fino all'effettivo punto di rottura del socket o della connessione.
	 * </p>
	 *
	 * @param message Il messaggio customizzato di livello applicativo che descrive 
	 * contestualmente l'operazione fallita (es. "Impossibile contattare ORS per l'indirizzo X").
	 * @param cause L'eccezione originale a basso livello sollevata dal client HTTP 
	 * (es. un'istanza di {@code org.springframework.web.client.RestClientException}).
	 */
	public GeocodingApiException(String message, Throwable cause) {
		super("Communication error with the Geocoding API: " + message, cause);
	}
}
