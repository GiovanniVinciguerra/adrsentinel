package dev.vinciguerra.adrsentinel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eccezione di dominio (Unchecked Exception) lanciata quando si tenta di eseguire un'operazione 
 * transazionale non consentita dall'attuale fase del ciclo di vita di una Spedizione (State Machine).
 * <p>
 * <b>Integrità della State Machine e Compliance Legale (ADR):</b><br>
 * Nel dominio architetturale di AdrSentinel, il ciclo di vita di un trasporto di merci pericolose è 
 * strettamente vincolato a normative legali. Una volta che una spedizione avanza oltre la fase di 
 * pianificazione (ossia perde lo stato di {@code PLANNED}), i suoi dati spaziali, i veicoli assegnati 
 * e le tratte vettoriali diventano legalmente vincolanti e immutabili ai fini degli Audit ministeriali. 
 * Questa eccezione agisce come una <i>Guard Clause</i> nel Service Layer per intercettare e bloccare 
 * immediatamente qualsiasi tentativo di mutazione retroattiva.
 * </p>
 * <p>
 * <b>Mappatura HTTP Automatica (RESTful Compliance):</b><br>
 * L'annotazione {@code @ResponseStatus(HttpStatus.CONFLICT)} istruisce il framework Spring MVC 
 * a catturare questa eccezione e tradurla nativamente in una response <b>HTTP 409 (Conflict)</b>. 
 * Questo rispetta pienamente la semantica REST: il server informa il client che il payload JSON 
 * è sintatticamente valido, ma la richiesta non può essere soddisfatta a causa di una violazione 
 * delle regole di stato della risorsa target.
 * </p>
 * <p>
 * <b>Rollback Transazionale:</b><br>
 * Estendendo {@link RuntimeException}, il sollevamento di questa eccezione innesca automaticamente 
 * il Rollback di qualsiasi transazione in corso (gestita tramite {@code @Transactional}), proteggendo 
 * il database da alterazioni parziali o stati spuri.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (State Machine & Compliance Guard)
 * @since 1.0
 * @see RuntimeException
 * @see org.springframework.web.bind.annotation.ResponseStatus
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalShipmentStateException extends RuntimeException {
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
	 * Costruisce una nuova eccezione iniettando il messaggio diagnostico di dettaglio.
	 * @param message La descrizione puntuale della violazione di stato (es. <i>"Update denied: 
	 * Shipment is no longer in PLANNED status"</i>). Questo messaggio viene generalmente 
	 * propagato nei file di log per scopi di tracciabilità (Audit Trail) e può essere 
	 * estratto dai Controller Advice per generare payload di errore standardizzati verso il frontend.
	 */
	public IllegalShipmentStateException(String message) {
        super(message);
    }
	
	/**
     * Costruisce una nuova eccezione {@code IllegalShipmentStateException} con il messaggio 
     * di dettaglio e la causa specificati.
     * <p>
     * Questo costruttore è particolarmente utile per l'incatenamento delle eccezioni 
     * (exception chaining), permettendo di preservare l'errore d'origine (ad esempio 
     * un {@link java.time.format.DateTimeParseException}) per facilitare il debugging.
     * </p>
     * @param message il messaggio di dettaglio che descrive l'errore (recuperabile 
     * tramite il metodo {@link #getMessage()})
     * @param cause   la causa che ha scatenato questa eccezione (recuperabile tramite 
     * il metodo {@link #getCause()}). Un valore {@code null} è ammesso 
     * e indica che la causa è inesistente o sconosciuta.
     */
	public IllegalShipmentStateException(String message, Throwable cause) {
		super(message, cause);
	}
}
