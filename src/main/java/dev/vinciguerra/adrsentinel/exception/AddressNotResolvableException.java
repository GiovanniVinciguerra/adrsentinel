package dev.vinciguerra.adrsentinel.exception;

/**
 * Eccezione di dominio (Domain Exception) non controllata (Unchecked) sollevata quando 
 * il sistema non è in grado di tradurre un indirizzo testuale in coordinate geospaziali valide.
 * <p>
 * <b>Contesto Architetturale:</b><br>
 * A differenza di un fallimento tecnico (es. timeout di rete o API key invalida gestiti 
 * tramite {@code GeocodingApiException}), questa eccezione denota un errore di logica 
 * di business (Semantico/Dati). Indica che la chiamata HTTP al provider cartografico 
 * ha avuto successo (HTTP 200), ma l'indirizzo fornito dall'utente è inesistente, 
 * incompleto o non indicizzabile sulle mappe.
 * </p>
 * <p>
 * <b>Gestione Consigliata (Presentation Layer):</b><br>
 * Si raccomanda di intercettare questa eccezione tramite un {@code @ControllerAdvice} 
 * (es. GlobalExceptionHandler) per mappare l'errore in una risposta REST semanticamente 
 * corretta verso il client. I codici HTTP consigliati sono:
 * <ul>
 * <li><b>HTTP 400 (Bad Request):</b> Se si considera l'indirizzo fornito come un input malformato.</li>
 * <li><b>HTTP 422 (Unprocessable Entity):</b> Ideale, indica che la sintassi è corretta ma i dati 
 * non sono processabili spazialmente.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 * @see RuntimeException
 */
public class AddressNotResolvableException extends RuntimeException {
	/**
	 * Identificatore univoco per la serializzazione della classe.
	 * Garantisce la compatibilità della versione dell'oggetto durante i processi 
	 * di marshalling/unmarshalling (es. se l'eccezione viaggia attraverso code di messaggi 
	 * o sistemi di tracciamento distribuiti).
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Costruisce una nuova eccezione specificando l'indirizzo esatto che ha causato il fallimento.
	 * Il messaggio generato è pronto per essere iniettato nei log applicativi (Audit) 
	 * o restituito nel payload di errore verso il frontend.
	 * @param address La stringa testuale (human-readable) dell'indirizzo che il provider 
	 * cartografico non è riuscito a geolocalizzare (es. "Via Fasulla 123, Springfield").
	 */
	public AddressNotResolvableException(String address) {
		super("The address provided could not be geolocated: '" + address + "'.");
	}
}
