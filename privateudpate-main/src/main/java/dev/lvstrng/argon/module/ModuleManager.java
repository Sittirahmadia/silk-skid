package dev.lvstrng.argon.module;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.client.Friends;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import dev.lvstrng.argon.module.modules.combat.*;
import dev.lvstrng.argon.module.modules.misc.Freecam;
import dev.lvstrng.argon.module.modules.misc.NoBreakDelay;
import dev.lvstrng.argon.module.modules.render.NoBounce;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		addModules();
		addKeybinds();
	}

	public void addModules() {
		// Client
		add(new ClickGUI());
		add(new SelfDestruct());
		add(new Friends());

		// Combat
		add(new Hitboxes());
		add(new AutoEXP());
		add(new CrystalOptimizer());

		add(new AutoCrystal());
		add(new AutoDoubleHand());
		add(new TriggerBot());
		add(new AimAssist());
		add(new DoubleAnchor());
		add(new AnchorExploder());
		add(new AnchorPlacer());
		add(new PlacementOptimizer());
		add(new AnchorMacroV2());
		add(new AutoInventoryTotem());
		add(new AutoInventoryTotemV4());

		// Misc
		add(new Freecam());
		add(new NoBreakDelay());

		// Render
		add(new NoBounce());
	}

	public List<Module> getEnabledModules() {
		return modules.stream().filter(Module::isEnabled).toList();
	}

	public List<Module> getModules() {
		return modules;
	}

	public void addKeybinds() {
		Argon.INSTANCE.getEventManager().add(ButtonListener.class, this);
		for (Module module : modules)
			module.addSetting(new KeybindSetting(
					EncryptedString.of("Keybind"), module.getKey(), true)
					.setDescription(EncryptedString.of("Key to enable the module")));
	}

	public List<Module> getModulesInCategory(Category category) {
		return modules.stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> moduleClass) {
		return (T) modules.stream()
				.filter(moduleClass::isInstance)
				.findFirst()
				.orElse(null);
	}

	public void add(Module module) {
		modules.add(module);
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (!SelfDestruct.destruct) {
			modules.forEach(module -> {
				if (module.getKey() == event.button && event.action == GLFW.GLFW_PRESS)
					module.toggle();
			});
		}
	}
}
