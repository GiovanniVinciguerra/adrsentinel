package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorVehicleType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e tassonomico {@link ValidatorVehicleType}.
 * Agisce come scudo architetturale (Edge Validation) per la classificazione dei veicoli nel dominio 
 * della logistica e dei trasporti (es. normativa ADR), garantendo che la nomenclatura fornita 
 * dal client corrisponda esattamente al dizionario di sistema.
 * <p><b>Design Architetturale (String-to-Enum Safe Binding):</b></p>
 * Questa classe implementa una tecnica avanzata di programmazione difensiva. Per evitare che payload 
 * malformati (contenenti typo o costanti inesistenti) inneschino eccezioni fatali durante la fase di 
 * deserializzazione JSON del framework (es. {@code HttpMessageNotReadableException}), il DTO accetta 
 * intenzionalmente una stringa "cruda". Questo validatore isola il tentativo di conversione in 
 * {@link VehicleType} all'interno di un blocco protetto, trasformando un potenziale crash di sistema 
 * in un errore di validazione elegante, controllato e standardizzato per il client REST.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorVehicleType
 */
public class VehicleTypeValidator implements ConstraintValidator<ValidatorVehicleType, String> {
	/**
	 * Esegue l'ispezione profonda della stringa in ingresso per certificarne l'obbligatorietà, 
	 * l'appartenenza al dizionario di dominio e la sicurezza per la persistenza.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defensive Try-Catch):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}, garantendo l'obbligatorietà del dato tassonomico.</li>
	 * <li><b>Domain Dictionary Binding:</b> Tenta la risoluzione dinamica della stringa contro 
	 * l'enumerazione di sistema {@code VehicleType}. Se la stringa non corrisponde esattamente a 
	 * una costante dell'Enum (case-sensitive), la {@code IllegalArgumentException} generata dalla 
	 * JVM viene silenziata e convertita in una violazione del vincolo (ritorno {@code false}).</li>
	 * <li><b>Database Guard-Rail:</b> Come ultima linea di difesa per la sicurezza della persistenza 
	 * (Data Truncation prevention), si assicura che la stringa non superi il limite classico dei 
	 * 255 caratteri (tipico per i campi VARCHAR su database relazionali).</li>
	 * </ol>
	 * @param value La stringa (cruda) rappresentante il tipo di veicolo, estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente, corrisponde a una costante valida 
	 * dell'enumerazione e rispetta i limiti di lunghezza del database; {@code false} in caso di 
	 * assenza, mismatch con il dizionario (typo/valore illegale) o stringa fuori misura.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			Enum.valueOf(VehicleType.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
