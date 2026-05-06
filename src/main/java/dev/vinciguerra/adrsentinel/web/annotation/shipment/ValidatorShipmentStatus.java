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
 * Vincolo di validazione perimetrale (Edge Validation) per il campo descrittivo 
 * dello stato logistico della spedizione.
 * <p><b>Contesto Architetturale (Ciclo di Vita e Macchina a Stati):</b></p>
 * Nel dominio gestionale, lo stato di una spedizione (es. CREATA, IN_TRANSITO, CONSEGNATA) 
 * rappresenta lo snodo cruciale per la logica di business e la tracciabilità del carico ADR. 
 * Questa annotazione agisce da barriera protettiva di base (Anti-Corruption Layer) 
 * per garantire l'integrità strutturale del payload in ingresso prima che questo 
 * venga processato e convertito dal livello Service.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Avvalendosi del pattern di Composizione di Annotazioni ({@code @Constraint(validatedBy = {})}), 
 * il vincolo si assicura che il dato rispetti i requisiti minimi di coerenza:
 * <ul>
 * <li><b>Esistenza Assoluta ({@code @NotNull}):</b> Assicura che la creazione o la transizione 
 * di stato non avvenga mai in modo "cieco" o con payload parziali. Un'operazione 
 * logistica deve sempre dichiarare il suo stato di arrivo.</li>
 * <li><b>Protezione dell'Infrastruttura Dati ({@code @Size}):</b> Fissa un limite architettonico 
 * protettivo di 255 caratteri. Sebbene i valori di stato validi (gli Enum) siano stringhe 
 * molto più brevi, questo vincolo è una <i>best practice</i> di sicurezza per prevenire payload 
 * gonfiati e blindare l'applicazione contro eccezioni di <i>Data Truncation</i> (Overflow) 
 * durante il salvataggio su colonne {@code VARCHAR(255)} del database relazionale.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per operare su campi testuali ({@code String}) 
 * all'interno dei Data Transfer Object (es. {@code ShipmentUpdateStatusDTO}) o direttamente 
 * sui parametri dei Controller REST.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Shipment status cannot be null")
@Size(
	max = 255,
	message = "Shipment status must not exceed 255 characters."
)
public @interface ValidatorShipmentStatus {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Shipment status is required and must not exceed 255 characters.";
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
