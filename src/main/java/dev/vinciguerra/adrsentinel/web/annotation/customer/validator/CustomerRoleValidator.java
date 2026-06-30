package dev.vinciguerra.adrsentinel.web.annotation.customer.validator;

import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validatore JSR-380 customizzato per garantire l'integrità referenziale e semantica dei ruoli 
 * logistici (Customer Role) provenienti dall'esterno del sistema.
 * <p>
 * <b>Contesto Architetturale (Anti-Corruption Layer):</b><br>
 * Nei DTO "flat" (appiattiti) per le API REST, i tipi complessi come gli Enum vengono spesso 
 * serializzati come semplici stringhe. Questa classe si posiziona al confine dell'applicazione 
 * per intercettare queste stringhe e verificare rigorosamente che corrispondano a una costante 
 * enumerata valida nel dominio di AdrSentinel, prevenendo errori di conversione e 
 * {@code IllegalArgumentException} a valle nel Service Layer.
 * </p>
 * <p>
 * <b>Meccanica di Validazione:</b>
 * <ul>
 * <li><b>Fail-Fast:</b> Rigetta immediatamente valori nulli o vuoti, delegando il controllo primario.</li>
 * <li><b>Type Checking Dinamico:</b> Sfrutta il metodo nativo {@code Enum.valueOf} per tentare il 
 * parsing della stringa. La cattura dell'eccezione viene utilizzata intenzionalmente come logica di 
 * ramificazione (Control Flow) per determinare l'invalidità del dato.</li>
 * <li><b>Allineamento DDL:</b> Include un controllo finale di sicurezza sulla lunghezza massima (255 caratteri), 
 * garantendo la perfetta compatibilità formale con le direttive {@code @Column} del database.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorCustomerRole
 */
public class CustomerRoleValidator implements ConstraintValidator<ValidatorCustomerRole, String> {
	/**
	 * Esegue l'ispezione della stringa in ingresso per validarne la corrispondenza 
	 * con l'enumerazione {@code CustomerRole}.
	 * @param value La stringa contenente il presunto ruolo logistico (es. "SENDER", "RECEIVER").
	 * @param context Il contesto di validazione, utilizzabile per iniettare messaggi di errore customizzati.
	 * @return {@code true} se la stringa non è vuota, corrisponde esattamente a una costante 
	 * dell'Enum (case-sensitive) e rispetta i limiti dimensionali del database. 
	 * {@code false} se il valore è nullo, malformato o inesistente nel dominio.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		try {
			Enum.valueOf(CustomerRole.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
