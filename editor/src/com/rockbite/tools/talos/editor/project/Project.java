package com.rockbite.tools.talos.editor.project;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.rockbite.tools.talos.TalosMain;
import com.rockbite.tools.talos.editor.ParticleEmitterWrapper;
import com.rockbite.tools.talos.editor.LegacyImporter;
import com.rockbite.tools.talos.editor.assets.ProjectAssetProvider;
import com.rockbite.tools.talos.editor.data.ModuleWrapperGroup;
import com.rockbite.tools.talos.editor.serialization.*;
import com.rockbite.tools.talos.editor.widgets.ui.ModuleBoardWidget;
import com.rockbite.tools.talos.editor.wrappers.ModuleWrapper;
import com.rockbite.tools.talos.runtime.ParticleEmitterDescriptor;
import com.rockbite.tools.talos.runtime.ParticleEffectInstance;
import com.rockbite.tools.talos.runtime.ParticleEffectDescriptor;
import com.rockbite.tools.talos.runtime.assets.AssetProvider;
import com.rockbite.tools.talos.runtime.modules.TextureModule;
import com.rockbite.tools.talos.runtime.serialization.ConnectionData;
import com.rockbite.tools.talos.runtime.serialization.ExportData;

import java.io.File;
import java.net.URISyntaxException;

public class Project {

	private ProjectData projectData;

	private ProjectSerializer projectSerializer;

	private Array<ParticleEmitterWrapper> activeWrappers = new Array<>();

	private ParticleEffectInstance particleEffect;
	private ParticleEffectDescriptor particleEffectDescriptor;
	private ParticleEmitterWrapper currentEmitterWrapper;

	private String currentProjectPath = null;

	private LegacyImporter importer;

	private ProjectAssetProvider projectAssetProvider;

	public Project () {
		projectAssetProvider = new ProjectAssetProvider();

		projectSerializer = new ProjectSerializer();
		particleEffectDescriptor = new ParticleEffectDescriptor();
		particleEffectDescriptor.setAssetProvider(projectAssetProvider);
		particleEffect = new ParticleEffectInstance(particleEffectDescriptor);
		particleEffect.loopable = true;

		importer = new LegacyImporter(TalosMain.Instance().NodeStage());
	}

	public void loadProject (FileHandle projectFileHandle) {
		if (projectFileHandle.exists()) {
			currentProjectPath = projectFileHandle.path();
			projectData = projectSerializer.read(projectFileHandle);

			cleanData();

			ParticleEmitterWrapper firstEmitter = null;

			for(EmitterData emitterData: projectData.getEmitters()) {
				IntMap<ModuleWrapper> map = new IntMap<>();

				ParticleEmitterWrapper emitterWrapper = createNewEmitter(emitterData.name);
				TalosMain.Instance().NodeStage().moduleBoardWidget.loadEmitterToBoard(emitterWrapper, emitterData);

				final ParticleEmitterDescriptor graph = emitterWrapper.getGraph();
				for (ModuleWrapper module : emitterData.modules) {
					map.put(module.getId(), module);

					graph.addModule(module.getModule());
					module.getModule().setModuleGraph(graph);
				}


				if(firstEmitter == null) {
					firstEmitter = emitterWrapper;
				}

				// time to load groups here
				for(GroupData group: emitterData.groups) {
					ObjectSet<ModuleWrapper> childWrappers = new ObjectSet<>();
					for(Integer id: group.modules) {
						if(map.get(id) != null) {
							childWrappers.add(map.get(id));
						}
					}
					ModuleWrapperGroup moduleWrapperGroup = TalosMain.Instance().NodeStage().moduleBoardWidget.createGroupForWrappers(childWrappers);
					Color clr = new Color();
					Color.abgr8888ToColor(clr, group.color);
					moduleWrapperGroup.setData(group.text, clr);
				}
			}

			if(firstEmitter != null) {
				TalosMain.Instance().Project().setCurrentEmitterWrapper(firstEmitter);
				TalosMain.Instance().NodeStage().moduleBoardWidget.setCurrentEmitter(firstEmitter);
			}

		} else {
			//Error handle
		}
	}

	public void saveProject() {
		if(isBoundToFile()) {
			FileHandle handle = Gdx.files.absolute(currentProjectPath);
			saveProject(handle);
		}
	}

	public void saveProject (FileHandle destination) {
		projectData.setFrom(TalosMain.Instance().NodeStage().moduleBoardWidget);
		projectSerializer.write(destination, projectData);

		currentProjectPath = destination.path();
	}

	public void loadDefaultProject() {
		FileHandle fileHandle = Gdx.files.internal("samples/fire.tls");
		if (fileHandle.exists()) {
			TalosMain.Instance().Project().loadProject(fileHandle);
		} else {
			newProject();
		}
	}

	public void newProject () {
		cleanData();
		projectData = new ProjectData();
		createNewEmitter("default_emitter");
		currentProjectPath = null;
	}

	private void cleanData() {
		TalosMain.Instance().NodeStage().moduleBoardWidget.clearAll();
		activeWrappers.clear();
		particleEffectDescriptor = new ParticleEffectDescriptor();
		particleEffectDescriptor.setAssetProvider(projectAssetProvider);
		particleEffect = new ParticleEffectInstance(particleEffectDescriptor);
		particleEffect.loopable = true;

		TalosMain.Instance().UIStage().setEmitters(activeWrappers);
	}

	public ParticleEffectInstance getParticleEffect () {
		return particleEffect;
	}

	public ParticleEmitterWrapper createNewEmitter (String emitterName) {
		ParticleEmitterWrapper emitterWrapper = new ParticleEmitterWrapper();
		emitterWrapper.setName(emitterName);

		ParticleEmitterDescriptor moduleGraph = TalosMain.Instance().Project().particleEffectDescriptor.createEmitterDescriptor();
		emitterWrapper.setModuleGraph(moduleGraph);

		activeWrappers.add(emitterWrapper);
		currentEmitterWrapper = emitterWrapper;

		particleEffect.addEmitter(moduleGraph);

		TalosMain.Instance().NodeStage().moduleBoardWidget.setCurrentEmitter(currentEmitterWrapper);
		TalosMain.Instance().UIStage().setEmitters(activeWrappers);

		return emitterWrapper;
	}


	public void addEmitter (ParticleEmitterWrapper emitterWrapper) {
		activeWrappers.add(emitterWrapper);
	}

	public void removeEmitter (ParticleEmitterWrapper wrapper) {
		particleEffect.removeEmitterForEmitterDescriptor(wrapper.getEmitter());
		particleEffectDescriptor.removeEmitter(wrapper.getEmitter());

		activeWrappers.removeValue(wrapper, true);
		TalosMain.Instance().NodeStage().onEmitterRemoved(wrapper);

		if (activeWrappers.size > 0) {
			currentEmitterWrapper = activeWrappers.peek();
		} else {
			currentEmitterWrapper = null;
		}
		TalosMain.Instance().UIStage().setEmitters(activeWrappers);

	}

	public void setCurrentEmitterWrapper (ParticleEmitterWrapper emitterWrapper) {
		this.currentEmitterWrapper = emitterWrapper;
	}

	public ParticleEmitterWrapper getCurrentEmitterWrapper () {
		return currentEmitterWrapper;
	}

	public ParticleEmitterDescriptor getCurrentModuleGraph () {
		return currentEmitterWrapper.getGraph();
	}

	public ProjectAssetProvider getProjectAssetProvider () {
		return projectAssetProvider;
	}

	public String getLocalPath() {
		try {
			return new File(this.getClass().getProtectionDomain().getCodeSource().getLocation()
					.toURI()).getParent();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		return "";
	}

	public boolean isBoundToFile() {
		return currentProjectPath != null;
	}

	public void importFromLegacyFormat(FileHandle fileHandle) {
		cleanData();
		importer.read(fileHandle);
		currentProjectPath = null;
	}

	public void resetCurrentProjectPath() {
		currentProjectPath = null;
	}

	public String getPath() {
		return currentProjectPath;
	}

	public void exportProject(FileHandle fileHandle) {
		ExportData exportData = new ExportData();
		setToExportData(exportData, TalosMain.Instance().NodeStage().moduleBoardWidget);
		projectSerializer.writeExport(fileHandle, exportData);
	}

	private void setToExportData (ExportData data, ModuleBoardWidget moduleBoardWidget) {
		final ObjectMap<ParticleEmitterWrapper, Array<ModuleWrapper>> moduleWrappers = moduleBoardWidget.moduleWrappers;
		final ObjectMap<ParticleEmitterWrapper, Array<ModuleBoardWidget.NodeConnection>> nodeConnections = moduleBoardWidget.nodeConnections;

		for (ParticleEmitterWrapper key : moduleWrappers.keys()) {
			final ExportData.EmitterExportData emitterData = new ExportData.EmitterExportData();
			emitterData.name = key.getName();
			for (ModuleWrapper wrapper : moduleWrappers.get(key)) {
				emitterData.modules.add(wrapper.getModule());

				if (wrapper.getModule() instanceof TextureModule) {
					TextureModule textureModule = (TextureModule)wrapper.getModule();
					String name = textureModule.regionName;
					if (name == null)
						name = "fire";
					if (name.contains(".")) {
						name = name.substring(0, name.lastIndexOf("."));
					}
					if (!data.metadata.resources.contains(name, false)) {
						data.metadata.resources.add(name);
					}
				}
			}

			final Array<ModuleBoardWidget.NodeConnection> nodeConns = nodeConnections.get(key);
			if (nodeConns != null) {
				for (ModuleBoardWidget.NodeConnection nodeConn : nodeConns) {
					emitterData.connections.add(new ConnectionData(nodeConn.fromModule.getModule().getIndex(), nodeConn.toModule.getModule().getIndex(), nodeConn.fromSlot, nodeConn.toSlot));
				}
			}

			data.emitters.add(emitterData);
		}

	}

}
