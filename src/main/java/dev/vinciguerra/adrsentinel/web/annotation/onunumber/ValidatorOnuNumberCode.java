package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.OnuNumberCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione perimetrale (Edge Validation) per il Numero ONU (UN Number).
 * <p><b>Contesto di Dominio (Normativa ADR):</b></p>
 * Il Numero ONU è l'identificativo univoco a quattro cifre assegnato dal Comitato di Esperti 
 * delle Nazioni Unite sul Trasporto di Merci Pericolose. Serve a classificare in modo 
 * inequivocabile sostanze, materie o oggetti pericolosi (es. {@code 1202} per il Gasolio, 
 * {@code 1203} per la Benzina). Questa annotazione funge da barriera (Anti-Corruption Layer) 
 * per assicurare l'integrità del dato logistico prima del suo ingresso nella logica di business.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Questo vincolo implementa il pattern avanzato della "Composizione di Annotazioni". 
 * Dichiarando un {@code @Constraint(validatedBy = {})} vuoto, delega l'intera validazione 
 * alla sinergia delle regole native sottostanti:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Intercetta i payload malevoli o incompleti, garantendo 
 * che una classificazione ADR non possa mai esistere senza il suo identificatore primario.</li>
 * <li><b>Integrità Strutturale ({@code @Pattern}):</b> Tramite la regex {@code ^\d{4}$}, impone 
 * che la stringa sia composta da <i>esattamente</i> quattro cifre numeriche. Respinge 
 * automaticamente spaziature, caratteri alfabetici o lunghezze anomale.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, fornisce una copertura totale sia sui 
 * Data Transfer Object (DTO) serializzati, sia sulle variabili di percorso (Path Variables) 
 * o parametri di query esposti nei Controller REST.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see OnuNumberCodeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { OnuNumberCodeValidator.class })
public @interface ValidatorOnuNumberCode {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: ONU number code is missing or invalid.";
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
