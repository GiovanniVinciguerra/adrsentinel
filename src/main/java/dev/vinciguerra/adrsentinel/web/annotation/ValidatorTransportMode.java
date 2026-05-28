package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.validator.TransportModeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione custom (Anti-Corruption Layer) progettato per blindare 
 * l'acquisizione della modalità di trasporto (es. Cisterna, Colli, Rinfusa).
 * <p>
 * Nel dominio logistico di AdrSentinel, sapere <i>come</i> la merce è imballata e 
 * trasportata è un'informazione di importanza critica. La modalità di trasporto 
 * determina l'applicabilità di specifiche normative ADR (es. obbligo di omologazione 
 * FL o AT per le cisterne) e funge da filtro insindacabile nell'algoritmo di 
 * matchmaking per la selezione del veicolo adeguato.
 * </p>
 * <p>
 * <b>Meccanica Architetturale:</b> Applicata a un campo di tipo {@code String} all'interno 
 * di un DTO, questa annotazione delega la validazione effettiva alla classe 
 * {@link TransportModeValidator}. Essa garantisce che il valore in ingresso sia non nullo, 
 * convertibile in modo sicuro nell'enumerazione di dominio (prevenendo eccezioni di 
 * deserializzazione JSON) e dimensionalmente coerente per un'eventuale persistenza.
 * </p>
 * <p>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 3.0
 * @see TransportModeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { TransportModeValidator.class })
public @interface ValidatorTransportMode {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: the required modality type is missing or not recognized.";
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
