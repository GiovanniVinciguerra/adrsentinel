package dev.vinciguerra.adrsentinel.web.annotation.customer.validator;

import java.util.List;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerContainer;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentRequestDTO.CustomerContainerDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validatore JSR-380 (Hibernate Validator) customizzato per la validazione strutturale e semantica 
 * della collezione degli attori logistici (Customers) associati a una spedizione.
 * <p>
 * <b>Contesto Architetturale:</b><br>
 * Questa classe agisce come barriera di validazione primaria (strato di Anti-Corruption) 
 * prima che il payload raggiunga il Service Layer. Assicura che la ripartizione dei ruoli 
 * all'interno del Data Transfer Object rispetti i vincoli stringenti del dominio applicativo.
 * </p>
 * <p>
 * <b>Regole di Business (Modello a Triangolazione Obbligatoria):</b><br>
 * Il validatore impone un modello logistico rigido in cui ogni spedizione deve coinvolgere 
 * esattamente tre attori distinti:
 * <ul>
 * <li>Esattamente un {@code SENDER} (Mittente).</li>
 * <li>Almeno uno o più di uno {@code RECEIVER} (Destinatario).</li>
 * <li>Esattamente un {@code CARRIER} (Vettore responsabile del trasporto).</li>
 * </ul>
 * Qualsiasi deviazione da questo schema (es. assenza del vettore, ruoli duplicati o ruoli sconosciuti) 
 * invalida interamente la richiesta (HTTP 400 Bad Request).
 * </p>
 * <p>
 * <b>Strategia di Ottimizzazione (Fail-Fast Pattern):</b><br>
 * L'algoritmo è progettato per minimizzare i cicli di CPU in caso di payload malevoli o malformati. 
 * Implementa controlli dimensionali preventivi in O(1) e, durante l'iterazione in O(N), 
 * utilizza uno <i>Switch Expression</i> che interrompe immediatamente l'esecuzione (early exit) 
 * al primo riscontro di un ruolo non previsto a sistema.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorCustomerContainer
 */
public class CustomerContainerValidator implements ConstraintValidator<ValidatorCustomerContainer, List<CustomerContainerDTO>> {
	/**
	 * Valuta l'integrità logica e quantitativa della lista dei clienti fornita nel payload.
	 * @param value La lista dei {@link CustomerContainerDTO} da validare.
	 * @param context Il contesto del validatore, utilizzabile per sovrascrivere o personalizzare 
	 * i messaggi di violazione del constraint.
	 * @return {@code true} se la collezione contiene esattamente 1 Sender, 1 Receiver e 1 Carrier. 
	 * {@code false} se la lista è nulla, vuota, di dimensione errata o contiene ruoli non validi.
	 */
	@Override
	public boolean isValid(List<CustomerContainerDTO> value, ConstraintValidatorContext context) {
		// 1. Controllo base: la collezione non deve essere nulla o vuota
		if(value == null || value.isEmpty())
			return false;
		// 2. La collezione deve contenere almeno 3 Customer (VatNumber)
		if(value.size() < 3)
			return false;
		int senderCount = 0;
		int receiverCount = 0;
		int carrierCount = 0;
		// 3. Conta le occorrenze dei ruoli ignorando i record malformati (gestiti dal @Valid interno).
		// Optimization trick: se cade nel default case vuol dire che il ruolo non è conforme e viene automaticamente invalidata tutta la richiesta.
		for(CustomerContainerDTO container : value) {
			if(container.role() == null) 
				continue;
			switch(container.role()) {
				case "SENDER" -> senderCount++;
				case "RECEIVER" -> receiverCount++;
				case "CARRIER" -> carrierCount++;
				default -> { return false; }
			}
		}
		return senderCount == 1 && carrierCount == 1 && receiverCount >= 1;
	}
}
