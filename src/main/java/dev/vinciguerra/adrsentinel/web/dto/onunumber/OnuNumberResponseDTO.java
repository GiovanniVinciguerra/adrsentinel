package dev.vinciguerra.adrsentinel.web.dto.onunumber;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;

public record OnuNumberResponseDTO(Long id, String onuCode, String name, PhysicalState physicalState, String kemlerCode, PackingGroup packingGroup, TunnelRestriction tunnelRestriction, Integer transportCategory, AdrClass adrClass) {}
