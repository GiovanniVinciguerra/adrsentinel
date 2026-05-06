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
import jakarta.validation.constraints.Size;

/**
 * Vincolo di validazione perimetrale (Edge Validation) per il Tracking Number, 
 * la Business Key primaria che identifica univocamente una spedizione nel sistema.
 * <p><b>Contesto Architetturale (Business Key & Caching):</b></p>
 * Nel dominio logistico, il Tracking Number non è un semplice identificatore, ma la 
 * chiave pubblica di accesso (spesso condivisa con i clienti o stampata sulle lettere di vettura) 
 * utilizzata per la tracciabilità, gli aggiornamenti di stato e le interrogazioni REST. 
 * Questa annotazione funge da "Anti-Corruption Layer", garantendo che le interrogazioni 
 * ai Controller e i tentativi di accesso alla Cache in memoria (es. {@code CaffeineCache}) 
 * avvengano sempre con chiavi strutturalmente valide.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Utilizzando il pattern della Composizione di Annotazioni ({@code @Constraint(validatedBy = {})}), 
 * il vincolo assicura l'integrità del dato attraverso due controlli:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Assicura che le richieste di lookup, tracciamento 
 * o aggiornamento non contengano mai chiavi nulle, bloccando alla radice potenziali 
 * {@code NullPointerException} nei layer sottostanti.</li>
 * <li><b>Integrità Strutturale ({@code @Size}):</b> Impone un limite massimo rigoroso di 36 caratteri. 
 * Questo limite non è casuale: corrisponde esattamente alla lunghezza standard di un 
 * Universal Unique Identifier (UUID v4) formattato come stringa (32 caratteri alfanumerici 
 * più 4 trattini). Previene iniezioni di payload massivi e allinea l'input al dimensionamento 
 * della colonna sul database relazionale.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per operare sia sui campi dei 
 * Data Transfer Object (DTO) in fase di inserimento, sia come salvaguardia sulle 
 * Path Variables (es. {@code @PathVariable @ValidatorTrackingNumber String tracking}) 
 * negli endpoint esposti dai Controller REST.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Tracking number must not be null")
@Size(
	max = 36,
	message = "Tracking number must not exceed 36 characters."
)
public @interface ValidatorTrackingNumber {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Tracking number is required and must not exceed 36 characters.";
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
