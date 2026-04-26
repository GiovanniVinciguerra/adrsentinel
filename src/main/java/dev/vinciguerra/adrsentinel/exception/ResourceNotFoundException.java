package dev.vinciguerra.adrsentinel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eccezione Custom di Dominio (Domain Exception) utilizzata per segnalare l'assenza 
 * di una risorsa fisica o logica richiesta all'interno del sistema (es. record non trovato nel database).
 * <p>
 * <b>Integrazione con Spring MVC:</b><br>
 * L'annotazione {@link ResponseStatus} istruisce il gestore globale delle eccezioni di Spring 
 * (es. {@code DefaultHandlerExceptionResolver} o un eventuale {@code @ControllerAdvice}) a intercettare 
 * questa eccezione e tradurla automaticamente in una risposta HTTP con status code 
 * <b>404 (Not Found)</b>, restituendola al client REST.
 * </p>
 * <p>
 * <b>Scelta Architetturale (Unchecked Exception):</b><br>
 * Estende {@link RuntimeException} (Unchecked) anziché {@code Exception} (Checked). 
 * Nelle architetture moderne basate su Spring Boot, si preferiscono le eccezioni Unchecked per gli 
 * errori di logica di business. Questo evita l'anti-pattern di dover propagare fastidiose clausole {@code throws} 
 * attraverso tutti i layer dell'applicazione (Controller -> Service -> Repository), mantenendo 
 * le firme dei metodi pulite ed eleganti.
 * </p>
 * <p>
 * <b>Pattern di Utilizzo Tipico:</b><br>
 * Questa eccezione esprime il suo massimo potenziale quando combinata con l'API {@link java.util.Optional} 
 * all'interno del Service Layer:
 * <pre>
 * {@code
 * return repository.findByLicensePlate(licensePlate)
 * .orElseThrow(() -> new ResourceNotFoundException("Veicolo non trovato con targa: " + licensePlate));
 * }
 * </pre>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 * @see org.springframework.web.bind.annotation.ResponseStatus
 * @see org.springframework.http.HttpStatus#NOT_FOUND
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
	/**
	 * Identificatore univoco per la serializzazione della classe.
	 * <p>
	 * Fisso a {@code 1L} per garantire la compatibilità in caso di deserializzazione, 
	 * rispettando il contratto dell'interfaccia {@link java.io.Serializable} (ereditata nativamente 
	 * dalla classe padre {@code Throwable}), ed evitando warning generati dal compilatore o dall'IDE.
	 * </p>
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Costruisce una nuova eccezione con il messaggio di dettaglio descrittivo.
	 * <p>
	 * Il messaggio fornito è di vitale importanza per i log di sistema e per il debugging. Inoltre, 
	 * se l'applicazione Spring Boot è configurata per esporre i messaggi d'errore 
	 * (es. tramite {@code server.error.include-message=always}), questo testo diventerà 
	 * direttamente visibile al frontend nel corpo del JSON di risposta, chiarendo 
	 * esattamente al chiamante quale risorsa ha generato l'errore 404.
	 * </p>
	 * @param message il messaggio di dettaglio che descrive la specifica risorsa mancante. 
	 * Viene conservato dalla JVM e reso accessibile tramite il metodo {@link #getMessage()}.
	 */
	public ResourceNotFoundException(String message) {
        super(message);
    }
}
