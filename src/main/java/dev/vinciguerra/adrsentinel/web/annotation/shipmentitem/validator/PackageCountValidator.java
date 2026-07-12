package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageCount;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorPackageCount}.
 * <p><b>Contesto Architetturale (Jakarta Bean Validation):</b></p>
 * Questa classe si inserisce nel ciclo di vita della validazione di Spring/Hibernate. 
 * Agisce come un filtro di sicurezza di primo livello (strato di trasporto o DTO) per intercettare 
 * input malformati prima che raggiungano la logica di business o il database, rispettando 
 * il principio di singola responsabilità (SRP).
 * <p><b>Regole di Dominio (ADR, Logistica e Sicurezza):</b></p>
 * Il validatore impone un limite strutturale al numero di colli (package count) basato su due criteri:
 * <ul>
 * <li><b>Coerenza Fisica:</b> Il limite superiore (9.999) rappresenta una soglia di tolleranza 
 * massima per il trasporto su gomma (es. autoarticolato standard). Un numero superiore è 
 * considerato fisicamente irrealistico per una singola riga di spedizione.</li>
 * <li><b>Prevenzione Errori (Typo Prevention):</b> Blocca alla radice errori umani di digitazione 
 * (es. un operatore che tiene premuto accidentalmente lo zero sulla tastiera), prevenendo 
 * la corruzione di calcoli statistici, logiche di carico o buffer overflow a valle.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPackageCount
 */
public class PackageCountValidator implements ConstraintValidator<ValidatorPackageCount, Integer> {
	/**
	 * Esegue la validazione effettiva sul valore numerico in ingresso.
	 * <p><b>Logica di validazione (Fail-Fast):</b></p>
	 * <ul>
	 * <li><i>Null-Safety:</i> Qualora il valore sia {@code null}, la validazione fallisce 
	 * immediatamente, delegando l'eventuale tolleranza dei null ad altre annotazioni (es. {@code @NotNull}).</li>
	 * <li><i>Range Check:</i> Valuta che il numero sia matematicamente compreso nel range consentito [0, 9999].</li>
	 * </ul>
	 * @param value Il numero di colli da validare, estratto dal DTO o dall'entità.
	 * @param context Il contesto di validazione fornito dal framework, utilizzabile per 
	 * personalizzare le constraint violation (non manipolato in questa implementazione).
	 * @return {@code true} se il valore rispetta i vincoli fisici e di sicurezza, {@code false} altrimenti.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value >= 0 && value <= 9999;
	}
}
