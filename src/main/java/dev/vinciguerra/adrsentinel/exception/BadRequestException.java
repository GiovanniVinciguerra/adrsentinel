package dev.vinciguerra.adrsentinel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eccezione di dominio custom per la segnalazione e gestione standardizzata 
 * delle richieste client non valide (HTTP 400 - Bad Request).
 * <p>
 * <b>Ruolo Architetturale (Exception Translation):</b><br>
 * Questa classe funge da ponte vitale tra la Business Logic (Service Layer) e il Presentation Layer 
 * (Controller REST). Grazie all'annotazione {@link ResponseStatus}, istruisce il framework 
 * Spring Boot (tramite il componente interno {@code ResponseStatusExceptionResolver}) a intercettare 
 * l'eccezione prima che provochi un collasso applicativo (HTTP 500). L'errore viene così tradotto 
 * automaticamente in una risposta HTTP strutturata, sicura e semanticamente corretta per il frontend.
 * </p>
 * <p>
 * <b>Semantica e Design (Liskov Substitution):</b><br>
 * Estendendo direttamente {@link IllegalArgumentException}, questa classe si allinea perfettamente 
 * agli standard del linguaggio Java. Indica in modo inequivocabile che il server è pronto e funzionante, 
 * ma l'azione è stata interrotta perché i dati forniti dal client (es. regole di business violate in un DTO, 
 * parametri discordanti, operazioni illecite) risultano inaccettabili per il dominio logistico.
 * </p>
 * <p>
 * <b>Riusabilità Trasversale:</b><br>
 * Essendo agnostica rispetto al dominio, rappresenta lo strumento standard per respingere 
 * input malevoli o privi di senso in qualsiasi modulo dell'infrastruttura (Spedizioni, Flotta, Catalogo), 
 * garantendo il principio del <i>Fail-Fast</i>.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Standardized Bad Request Handler)
 * @since 1.0
 * @see IllegalArgumentException
 * @see org.springframework.web.bind.annotation.ResponseStatus
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends IllegalArgumentException {
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
	 * esattamente al chiamante quale risorsa ha generato l'errore 400.
	 * </p>
	 * @param message il messaggio di dettaglio che descrive la specifica risorsa mancante. 
	 * Viene conservato dalla JVM e reso accessibile tramite il metodo {@link #getMessage()}.
	 */
	public BadRequestException(String message) {
		super(message);
	}
}
