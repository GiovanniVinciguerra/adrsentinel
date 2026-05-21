package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.validator.LocalDateValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione perimetrale (Edge Validation) per le date di spedizione 
 * e i riferimenti temporali formattati come stringhe.
 * <p><b>Contesto Architetturale (Standardizzazione ISO 8601):</b></p>
 * Nei sistemi distribuiti e nelle API REST, lo scambio di date è uno dei punti più critici 
 * per l'integrità dei dati. Questa annotazione impone l'uso del rigoroso standard 
 * internazionale ISO 8601 ({@code YYYY-MM-DD}). Funge da "Anti-Corruption Layer", 
 * bloccando formati regionali ambigui (es. {@code DD/MM/YYYY} o {@code MM/DD/YYYY}) 
 * prima che raggiungano i parser interni di Jackson o la logica di business.
 * <p><b>Motore di Validazione (Constraint Composition e Regex):</b></p>
 * Delegando l'esecuzione alla sinergia di annotazioni native ({@code @Constraint(validatedBy = {})}), 
 * il vincolo opera su due livelli:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Garantisce che la stringa temporale sia sempre 
 * dichiarata nel payload, prevenendo errori di logistica per spedizioni senza data.</li>
 * <li><b>Integrità Strutturale ({@code @Pattern}):</b> Valida sintatticamente la stringa 
 * tramite una complessa espressione regolare:
 * <ul>
 * <li>{@code ^\d{4}-}: Impone l'inizio con un anno a 4 cifre seguito da trattino.</li>
 * <li>{@code (0[1-9]|1[0-2])-}: Limita il blocco centrale ai 12 mesi validi 
 * (da 01 a 12), seguito da trattino.</li>
 * <li>{@code (0[1-9]|[12]\d|3[01])$}: Assicura che i giorni finali siano compresi 
 * tra 01 e 31.</li>
 * </ul>
 * </li>
 * </ul>
 * <i>Nota: Questa annotazione garantisce la correttezza formale della stringa. La validità 
 * logica del calendario (es. blocco del 30 Febbraio) sarà demandata alla successiva 
 * conversione in {@link java.time.LocalDate}.</i>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per essere applicata 
 * su campi di tipo {@code String} all'interno dei DTO in ingresso o su 
 * {@code @RequestParam} / {@code @PathVariable} nei Controller REST.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LocalDateValidator.class })
public @interface ValidatorLocalDate {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: the required date is missing or invalid (expected format: YYYY-MM-DD).";
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
