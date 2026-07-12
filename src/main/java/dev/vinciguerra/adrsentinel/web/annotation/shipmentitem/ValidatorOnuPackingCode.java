package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator.OnuPackingCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) specifica per il dominio ADR.
 * Viene utilizzata per marcare in modo dichiarativo i campi o i parametri che devono contenere 
 * uno o più codici di omologazione ONU per gli imballaggi (ONU Packing Codes).
 * <p><b>Ruolo Architetturale (Boundary Protection &amp; Fail-Fast):</b></p>
 * L'annotazione funge da punto di ingresso per il motore di validazione gestito da {@link OnuPackingCodeValidator}. 
 * Posizionata sui DTO di richiesta REST o sui parametri dei controller, permette di intercettare e respingere 
 * immediatamente i payload strutturalmente non validi (HTTP 400 Bad Request), impedendo che stringhe corrotte 
 * o fuori formato transitino verso i servizi di business o tentino la persistenza sul database.
 * <p><b>Regole di Validazione e Sintassi Ammessa:</b></p>
 * Il vincolo sottostante impone una precisa "grammatica" conforme ai rigidi standard logistici ADR:
 * <ul>
 * <li><b>Controllo Dimensionale:</b> La stringa complessiva deve avere una lunghezza minima di 2 caratteri 
 * (es. {@code 4G}) e massima di 15 caratteri, allineandosi perfettamente alle restrizioni fisiche delle colonne sul DB 
 * ed evitando eccezioni di troncamento dei dati.</li>
 * <li><b>Grammatica del Codice:</b> Ogni singolo codice deve aprirsi con un identificativo numerico di categoria 
 * (es. 1-9 per colli, 10-39 per grandi imballaggi/IBC), seguito da una o due lettere per il materiale (es. A, G, HA) 
 * e opzionalmente da un numero finale per la variante costruttiva (es. {@code 1A1}, {@code 31HA1}).</li>
 * <li><b>Multi-Codice e Separatori:</b> È ammessa la concatenazione di più codici diversi (fino a un massimo di 4) 
 * per gestire imballaggi misti all'interno della stessa riga di spedizione, a patto che siano rigorosamente separati 
 * da una virgola ({@code ,}) o da un trattino ({@code -}).</li>
 * </ul>
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @see OnuPackingCodeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { OnuPackingCodeValidator.class })
public @interface ValidatorOnuPackingCode {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: the required onu packing code is missing or invalid (expected 3-15 alphanumeric characters).";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici Validation Groups.
	 * <p>Utile per differenziare i controlli a seconda del contesto (es. Creazione vs Aggiornamento).</p>
	 * In questo caso è lasciato volutamente vuoto.
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo.
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente di allegare metadati informativi (Payload) alla violazione del vincolo, 
	 * tipicamente utilizzati per definire il livello di severità dell'errore.
	 * <p>Volutamente lasciato vuoto in questo caso.</p>
	 * @return l'array delle classi payload associate.
	 */
	Class<? extends Payload>[] payload() default {};
}
