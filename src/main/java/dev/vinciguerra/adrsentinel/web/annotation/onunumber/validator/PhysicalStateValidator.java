package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPhysicalState;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorPhysicalState}.
 * Agisce come barriera di controllo (Edge Validation) per garantire che lo stato fisico 
 * della merce pericolosa (ADR) fornito dal client sia obbligatoriamente presente e 
 * corrisponda esattamente a uno dei valori predefiniti nel dominio applicativo.
 * <p><b>Design Architetturale (Strict Validation & Type-Safety):</b></p>
 * Questa classe applica un pattern restrittivo a validazione multipla. Fonde il controllo 
 * di presenza logica (assorbendo il comportamento nativo di {@code @NotBlank}) con un 
 * rigoroso controllo semantico a dizionario (Enum Matching). Questo isolamento previene 
 * la propagazione di stringhe infette o non riconosciute, garantendo che il livello di 
 * servizio (Service Layer) non subisca mai eccezioni di conversione a runtime.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPhysicalState
 */
public class PhysicalStateValidator implements ConstraintValidator<ValidatorPhysicalState, String> {
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne la validità strutturale e semantica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defense in Depth):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Controllo di Presenza):</b> Intercetta e respinge immediatamente 
	 * valori {@code null}, stringhe vuote o composte esclusivamente da spazi bianchi 
	 * (tramite {@code isBlank()}). Questo passaggio impone l'assoluta obbligatorietà del dato.</li>
	 * <li><b>Risoluzione a Dizionario (Enum Matching):</b> Sottopone la stringa a un tentativo 
	 * di risoluzione verso l'enumerazione di dominio {@code PhysicalState}. Se il valore inviato 
	 * è un refuso o un dato inventato, la conseguente {@code IllegalArgumentException} viene 
	 * intercettata e convertita in un pulito fallimento di validazione (HTTP 400).</li>
	 * <li><b>Boundary Check (Defense in Depth):</b> Come ultimo strato di sicurezza, verifica 
	 * che la stringa rientri nei limiti dimensionali del database (es. VARCHAR(255)). Sebbene 
	 * il matching con l'Enum filtri già implicitamente stringhe troppo lunghe, questa riga 
	 * certifica matematicamente la sicurezza dell'archiviazione.</li>
	 * </ol>
	 * @param value La stringa rappresentante lo stato fisico estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente, formalmente sicura e 
	 * riconosciuta come costante valida dell'Enum; {@code false} in caso di assenza, 
	 * formato non supportato o violazione dimensionale.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		try {
			Enum.valueOf(PhysicalState.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
