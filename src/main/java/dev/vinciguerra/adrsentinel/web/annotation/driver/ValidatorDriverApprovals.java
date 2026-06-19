package dev.vinciguerra.adrsentinel.web.annotation.driver;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.driver.validator.DriverApprovalsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) per il dominio ADR.
 * <p>
 * Questa interfaccia definisce un vincolo (Constraint) architetturale applicabile a livello 
 * di campo o parametro per garantire l'integrità formale delle omologazioni associate a un conducente.
 * Funge da punto di aggancio (Hook) per il validatore concreto {@link DriverApprovalsValidator}, 
 * il quale eseguirà l'ispezione profonda (Deep Inspection) della struttura dati annotata.
 * </p>
 * <p><b>Contesto Architetturale (Fail-Fast & Clean Architecture):</b></p>
 * L'uso di un'annotazione dichiarativa centralizza la logica di validazione sul Presentation Layer,
 * disaccoppiandola completamente dalla Business Logic. Questo promuove il principio di <i>Fail-Fast</i>:
 * i payload malformati vengono intercettati e bloccati dal framework (generando un'eccezione 
 * {@code MethodArgumentNotValidException}) ancor prima di raggiungere i layer di servizio sottostanti.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see DriverApprovalsValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { DriverApprovalsValidator.class })
public @interface ValidatorDriverApprovals {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: provided driver approvals contain unrecognized or empty values.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità dell'errore (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
