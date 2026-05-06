package dev.vinciguerra.adrsentinel.web.annotation.shipment;

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
 * Vincolo di validazione perimetrale (Edge Validation) per il dato telemetrico 
 * della distanza di instradamento (es. chilometri o metri).
 * <p><b>Contesto Architetturale (Logistica e Routing):</b></p>
 * Nel dominio dei trasporti (specialmente merci pericolose ADR), la distanza percorsa 
 * non è un mero dato statistico, ma il parametro fondamentale per il calcolo dei noli, 
 * la stima del carburante, la valutazione del rischio di tratta e la pianificazione 
 * dei tempi di guida legali. Questa annotazione agisce come "Anti-Corruption Layer", 
 * assicurando che i dati metrici derivati dai sistemi esterni di routing (es. Google Maps API, OSRM) 
 * siano coerenti e fisicamente possibili prima di essere processati dal livello applicativo.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Sfruttando la delegazione ai vincoli nativi ({@code @Constraint(validatedBy = {})}), 
 * il validatore orchestra due livelli di controllo matematico:
 * <ul>
 * <li><b>Obbligatorietà del Routing ({@code @NotNull}):</b> Garantisce che la distanza 
 * sia sempre calcolata e fornita nel payload. Previene l'inserimento di spedizioni 
 * "cieche" prive di quantificazione spaziale.</li>
 * <li><b>Coerenza Fisica ({@code @Positive}):</b> Impone che il valore numerico sia 
 * strettamente maggiore di zero ({@code > 0}). Respinge automaticamente valori negativi 
 * o pari a zero (una spedizione logistica implica, per definizione, un reale 
 * spostamento fisico tra un'Origine e una Destinazione distinte).</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per operare nativamente 
 * su tipi di dato numerici (es. {@code Integer}, {@code Double}, {@code BigDecimal}) 
 * all'interno dei Data Transfer Object (DTO) in fase di pianificazione o aggiornamento.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Distance cannot be null. Route calculation is mandatory.")
@Positive(message = "Distance must be strictly greater than zero")
public @interface ValidatorShipmentDistance {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il valore strettamente 
	 * positivo.
	 */
	String message() default "Invalid Distance format or missing.";
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
