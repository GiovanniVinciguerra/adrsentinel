package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import java.util.Set;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorVehicleApprovals;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorVehicleApprovals}.
 * Ispeziona in profondità una collezione di stringhe per garantire che ogni elemento sia mappabile 
 * nel dizionario di dominio delle omologazioni ADR.
 * <p><b>Contesto Architetturale (Anti-Corruption Layer & Edge Validation):</b></p>
 * Questa classe agisce come uno scudo di sicurezza (Firewall Applicativo) posizionato 
 * sul Presentation Layer. Previene la vulnerabilità da <i>Payload Injection</i> bloccando 
 * stringhe malformate, nulle o inesistenti prima che queste possano raggiungere il Service Layer 
 * e scatenare eccezioni a runtime (es. {@code IllegalArgumentException} da parsing di Enum).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorVehicleApprovals
 */
public class VehicleApprovalsValidator implements ConstraintValidator<ValidatorVehicleApprovals, Set<String>> {
	/**
	 * Esegue l'ispezione profonda (Deep Inspection) della collezione fornita dal payload JSON.
	 * <p><b>Flusso di Validazione e Regole di Business:</b></p>
	 * <ul>
	 * <li><b>1. Tolleranza dell'Assenza (Optionality):</b> Se il set è {@code null}, la validazione 
	 * ha esito positivo. Questo supporta il caso d'uso di un veicolo privo di omologazioni ADR, 
	 * delegando la creazione di un Set vuoto al layer di mappatura sottostante (Service).</li>
	 * <li><b>2. Esecuzione Implicita (Empty Set):</b> Grazie all'architettura snella del ciclo {@code for-each}, 
	 * se il client invia un array vuoto ({@code []}), il corpo del ciclo viene naturalmente bypassato 
	 * con un costo computazionale trascurabile (O(1)), ritornando immediatamente {@code true}.</li>
	 * <li><b>3. Integrità del Contenuto (Blank Check):</b> Impedisce la presenza di valori {@code null}, 
	 * stringhe vuote o composte da soli spazi bianchi (grazie a {@code isBlank()}) all'interno dell'array.</li>
	 * <li><b>4. Risoluzione a Dizionario (Enum Dictionary Match):</b> Tenta il casting sicuro verso 
	 * l'enumerazione di dominio {@link VehicleApproval}. La gestione tramite blocco {@code try-catch} 
	 * agisce da <i>Fail-Fast</i>: al primo elemento non riconosciuto, l'intero payload viene 
	 * invalidato (HTTP 400).</li>
	 * </ul>
	 * @param values La collezione di stringhe grezze proveniente dal Data Transfer Object (Request Payload).
	 * @param context Il contesto di validazione fornito dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la collezione è assente, vuota o contiene esclusivamente costanti Enum valide; 
	 * {@code false} se contiene valori nulli, vuoti o stringhe non presenti nel dominio.
	 */
	@Override
	public boolean isValid(Set<String> values, ConstraintValidatorContext context) {
		if(values == null)
			return true;
		for(String value : values) {
			if(value == null || value.trim().isBlank())
				return false;
			try {
				Enum.valueOf(VehicleApproval.class, value);
			} catch(IllegalArgumentException error) {
				return false;
			}
		}
		return true;
	}
}
