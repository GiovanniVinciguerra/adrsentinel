package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Vincolo di validazione perimetrale centralizzato (Macro-Vincolo) per campi numerici 
 * obbligatori e strettamente positivi.
 * <p><b>Contesto Architetturale (DRY e Grandezze Fisiche):</b></p>
 * Questa meta-annotazione nasce dal consolidamento architetturale di molteplici regole di 
 * validazione ridondanti (es. distanze di routing, quantità di articoli, pesi). 
 * Nel dominio della logistica ADR, i valori numerici rappresentano grandezze fisiche reali 
 * che non possono essere omesse (pena l'impossibilità di procedere con i calcoli di business) 
 * né assumere valori negativi o nulli. Questo validatore agisce come "Anti-Corruption Layer" 
 * universale per tutte le metriche quantitative.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Sfruttando il pattern della composizione dei vincoli ({@code @Constraint(validatedBy = {})}), 
 * l'annotazione orchestra simultaneamente due barriere matematiche:
 * <ul>
 * <li><b>Esistenza Assoluta ({@code @NotNull}):</b> Garantisce che il payload contenga sempre 
 * il dato quantitativo, prevenendo {@code NullPointerException} nei layer di servizio o 
 * conversioni errate dovute all'assenza del campo.</li>
 * <li><b>Coerenza Fisica ({@code @Positive}):</b> Respinge automaticamente valori {@code <= 0}. 
 * Assicura che una distanza, un peso o una quantità implichino un'entità reale e tangibile 
 * prima dell'inserimento nel database.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Progettata per essere applicata su campi di tipo Wrapper numerico (es. {@code Float}, 
 * {@code Double}, {@code Integer}, {@code BigDecimal}) all'interno dei DTO. 
 * <i>Nota: non applicare sui tipi primitivi (es. {@code double}), in quanto il loro 
 * valore di default (0.0) bypasserebbe il controllo {@code @NotNull} fallendo poi sul {@code @Positive}.</i>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Number cannot be null. Route calculation is mandatory.")
@Positive(message = "Number must be strictly greater than zero")
public @interface ValidatorRequiredNumber {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il valore strettamente 
	 * positivo.
	 */
	String message() default "Invalid Number format or missing.";
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
