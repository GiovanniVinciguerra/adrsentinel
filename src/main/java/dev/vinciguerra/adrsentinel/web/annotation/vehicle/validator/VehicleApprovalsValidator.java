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
	 * <li><b>1. Obbligatorietà del Dato (Strict Presence):</b> Il set non ammette l'assenza ({@code null}). 
	 * Se il client omette il nodo JSON associato, la validazione fallisce immediatamente agendo da 
	 * barriera <i>Fail-Fast</i>. Per rappresentare il caso d'uso di un veicolo privo di omologazioni ADR, 
	 * il client è obbligato a inviare esplicitamente un array vuoto ({@code []}).</li>
	 * <li><b>2. Esecuzione Implicita (Empty Set):</b> Grazie all'architettura del ciclo {@code for-each}, 
	 * se il payload contiene un array vuoto ({@code []}), il corpo dell'iterazione viene naturalmente 
	 * bypassato. L'operazione si conclude in tempo costante (O(1)) ritornando {@code true}, 
	 * validando correttamente l'assenza di certificazioni.</li>
	 * <li><b>3. Integrità del Contenuto (Blank Check):</b> Invalida il payload alla presenza di elementi 
	 * {@code null}, stringhe vuote o sequenze composte da soli spazi bianchi all'interno della collezione.</li>
	 * <li><b>4. Risoluzione a Dizionario (Enum Dictionary Match):</b> Tenta il casting sicuro verso 
	 * l'enumerazione di dominio {@link VehicleApproval}. La gestione tramite blocco {@code try-catch} 
	 * garantisce che, al primo valore non mappabile nel dizionario ADR, l'intera richiesta venga 
	 * respinta (HTTP 400).</li>
	 * </ul>
	 * @param values La collezione di stringhe grezze proveniente dal Data Transfer Object (Request Payload).
	 * @param context Il contesto di validazione fornito dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la collezione è istanziata (anche vuota) e contiene esclusivamente costanti Enum valide; 
	 * {@code false} se la collezione è {@code null}, o se uno dei suoi elementi risulta nullo, vuoto o non appartenente al dominio.
	 */
	@Override
	public boolean isValid(Set<String> values, ConstraintValidatorContext context) {
		if(values == null)
			return false;
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
