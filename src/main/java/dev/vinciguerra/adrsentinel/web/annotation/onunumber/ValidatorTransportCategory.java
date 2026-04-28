package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;

/**
 * Annotazione di validazione custom (Constraint Composition) per la verifica formale 
 * della categoria di trasporto (TransportCategory) di un Numero ONU.
 * <p>
 * <b>Pattern Architetturale (DRY & Single Source of Truth):</b><br>
 * Questa meta-annotazione aggrega e centralizza due vincoli fondamentali del database e di business:
 * <ul>
 * <li><b>Obbligatorietà ({@code @NotNull}):</b> Previene l'inserimento di anagrafiche ONU prive 
 * di categoria di trasporto (es. payload JSON mancanti).</li>
 * </ul>
 * </p>
 * <p>
 * <b>Delega di Validazione e Comportamento Unificato:</b><br>
 * L'attributo {@code validatedBy = {}} delega interamente l'esecuzione al motore interno di Hibernate Validator, 
 * evitando la creazione di classi validatrici superflue. L'uso dell'annotazione 
 * {@code @ReportAsSingleViolation} assicura che, a prescindere da quale vincolo interno fallisca 
 * (il valore è nullo OPPURE è troppo lungo), venga restituito al client un solo ed inequivocabile 
 * messaggio di errore formattato.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Transport category cannot be null.")
public @interface ValidatorTransportCategory {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Transport category cannot be null.";
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
