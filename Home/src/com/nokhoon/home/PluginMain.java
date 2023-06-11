package com.nokhoon.home;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

public class PluginMain extends JavaPlugin implements Listener {
	private final TextComponent NO_DATA = PluginConstants.warning("좌표를 저장하지 않았습니다. /home set <id> 명령어로 저장할 수 있습니다.");
	private final TextComponent NO_INPUT = PluginConstants.error("좌표의 id를 입력하세요.");
	private final TextComponent NOT_READY = PluginConstants.warning("아직 준비되지 않았거나 안전하지 않은 장소에 있습니다.");
	private final TextComponent DIFFERENT_DIMENSION = PluginConstants.warning("목적지가 다른 세계에 있습니다.");
	private final TextComponent HOME_FULL = PluginConstants.warning("더 이상 저장할 수 없습니다. 기존 좌표를 삭제하세요.");
	private final int MAX_HOMES = 10;
	
	private java.util.HashMap<UUID, Long> lastUseTable;
	private List<String> FIRST_ARGUMENTS = Arrays.asList("get", "go", "remove", "set");
	private Pattern validId = Pattern.compile("[A-Za-z0-9_-]{1,16}");
	
	private boolean isReady(Player player) {
		if(player.isDead() || player.isInsideVehicle()) return false;
		if(((Entity) player).isOnGround() == false) return false;
		if(player.getFireTicks() > 0
				|| player.getRemainingAir() < player.getMaximumAir()
				|| player.getFreezeTicks() > 0) return false;
		for(PotionEffect p : player.getActivePotionEffects()) {
			PotionEffectType type = p.getType();
			if(type == PotionEffectType.BLINDNESS) return false;
			if(type == PotionEffectType.CONFUSION) return false;
			if(type == PotionEffectType.GLOWING) return false;
			if(type == PotionEffectType.HUNGER) return false;
			if(type == PotionEffectType.LEVITATION) return false;
			if(type == PotionEffectType.POISON) return false;
			if(type == PotionEffectType.SLOW) return false;
			if(type == PotionEffectType.SLOW_DIGGING) return false;
			if(type == PotionEffectType.WEAKNESS) return false;
			if(type == PotionEffectType.WITHER) return false;
		}
		return System.currentTimeMillis() > lastUseTable.get(player.getUniqueId()) + 9999;
	}
	
	private void sendCommandUsage(Audience audience) {
		audience.sendMessage(PluginConstants.info("/home 명령어 사용 방법"));
		audience.sendMessage(Component.text("/home set <id> ", NamedTextColor.GRAY)
				.append(Component.text("현재 위치를 저장합니다.", NamedTextColor.WHITE)));
		audience.sendMessage(Component.text("/home get <id> ", NamedTextColor.GRAY)
				.append(Component.text("저장된 좌표를 확인합니다.", NamedTextColor.WHITE)));
		audience.sendMessage(Component.text("/home go <id> ", NamedTextColor.GRAY)
				.append(Component.text("저장된 좌표로 이동합니다.", NamedTextColor.WHITE)));
		audience.sendMessage(Component.text("/home remove <id> ", NamedTextColor.GRAY)
				.append(Component.text("저장된 좌표를 제거합니다.", NamedTextColor.WHITE)));
	}
	
	private String timePath(UUID id) {
		return id.toString() + ".time";
	}
	
	private String worldPath(UUID id, String home) {
		return id.toString() + "." + home + ".world";
	}
	
	private String xPath(UUID id, String home) {
		return id.toString() + "." + home + ".x";
	}
	
	private String yPath(UUID id, String home) {
		return id.toString() + "." + home + ".y";
	}
	
	private String zPath(UUID id, String home) {
		return id.toString() + "." + home + ".z";
	}
	
	private String yawPath(UUID id, String home) {
		return id.toString() + "." + home + ".yaw";
	}
	
	private String pitchPath(UUID id, String home) {
		return id.toString() + "." + home + ".pitch";
	}
	
	@Override
	public void onEnable() {
		lastUseTable = new HashMap<UUID, Long>(10, 0.5F);
		long defaultTime = System.currentTimeMillis() - 5000;
		for(Player player : getServer().getOnlinePlayers()) {
			long playerTime = defaultTime;
			long extraTime = getConfig().getLong(timePath(player.getUniqueId()), 0);
			if(extraTime < 0) playerTime -= extraTime;
			lastUseTable.put(player.getUniqueId(), playerTime);
		}
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		long currentTime = System.currentTimeMillis();
		var config = getConfig();
		
		for(Entry<UUID, Long> entry : lastUseTable.entrySet()) {
			long time = entry.getValue();
			long extra = currentTime - entry.getValue() - 10000;
			if(extra < 0) time = extra;
			config.set(timePath(entry.getKey()), time);
		}
		saveConfig();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(label.equalsIgnoreCase("home")) {
			Audience audience = (Audience) sender;
			if(sender instanceof Player) {
				Player player = (Player) sender;
				if(args.length == 0) sendCommandUsage(player);
				else {
					long currentTime = System.currentTimeMillis();
					UUID uuid = player.getUniqueId();
					Environment dimension = player.getWorld().getEnvironment();
					
					switch(args[0].toLowerCase()) {
					case "get" -> {
						if(args.length == 1) audience.sendMessage(NO_INPUT);
						else {
							String home = args[1];
							String homeDimension = getConfig().getString(worldPath(uuid, home), "none");
							
							if(homeDimension.equals("overworld") || homeDimension.equals("nether") || homeDimension.equals("end")) {
								double x = getConfig().getDouble(xPath(uuid, home));
								double y = getConfig().getDouble(yPath(uuid, home));
								double z = getConfig().getDouble(zPath(uuid, home));
								float yaw = (float) getConfig().getDouble(yawPath(uuid, home));
								float pitch = (float) getConfig().getDouble(pitchPath(uuid, home));
								World world = null;
								for(World w : getServer().getWorlds()) {
									Environment e = w.getEnvironment();
									if((homeDimension.equals("overworld") && e == Environment.NORMAL)
										|| (homeDimension.equals("nether") && e == Environment.NETHER)
										|| (homeDimension.equals("end") && e == Environment.THE_END)) {	
										world = w;
										break;
									}
								}
								var block = world.getBlockAt((int) Math.floor(x), (int) Math.ceil(y), (int) Math.floor(z));
								var floor = block.getRelative(0, -1, 0);
								
								audience.sendMessage(PluginConstants.info("저장된 좌표의 정보입니다."));
								audience.sendMessage(Component.text("세계 ", NamedTextColor.GREEN)
										.append(Component.text(homeDimension, NamedTextColor.WHITE)));
								audience.sendMessage(Component.text("좌표 ", NamedTextColor.GREEN)
										.append(Component.text("(" + x + ", " + y + ", " + z + ")", NamedTextColor.WHITE)));
								audience.sendMessage(Component.text("시선 ", NamedTextColor.GREEN)
										.append(Component.text("(" + yaw + ", " + pitch + ")", NamedTextColor.WHITE)));
								audience.sendMessage(Component.text("블록 ", NamedTextColor.GREEN)
										.append(Component.translatable(block.getType().translationKey(), NamedTextColor.WHITE)));
								audience.sendMessage(Component.text("바닥 ", NamedTextColor.GREEN)
										.append(Component.translatable(floor.getType().translationKey(), NamedTextColor.WHITE)));
								if(isReady(player)) audience.sendMessage(Component.text("/home go 준비됨", NamedTextColor.GREEN));
								else audience.sendMessage(Component.text("/home go 준비되지 않음", NamedTextColor.RED));
							}
							else audience.sendMessage(NO_DATA);
						}
					}
					case "set" -> {
						if(args.length == 1) audience.sendMessage(NO_INPUT);
						else if(args[1].equalsIgnoreCase("time")) {
							audience.sendMessage(PluginConstants.error("플러그인에서 사용 중인 이름입니다. 다른 id를 입력해주세요!"));
						}
						else if(validId.matcher(args[1]).matches()) {
							if(isReady(player)) {
								var homes = getConfig().getConfigurationSection(uuid.toString()).getKeys(false);
								homes.remove("time");
								homes.remove(args[1]);
								if(homes.size() >= MAX_HOMES) audience.sendMessage(HOME_FULL);
								else {
									Location location = player.getLocation();
									String world = switch(dimension) {
									case NETHER -> { yield "nether"; }
									case NORMAL -> { yield "overworld"; }
									case THE_END -> { yield "end"; }
									case CUSTOM -> { yield dimension.toString(); }
									};
									getConfig().set(worldPath(uuid, args[1]), world);
									getConfig().set(xPath(uuid, args[1]), location.getX());
									getConfig().set(yPath(uuid, args[1]), location.getY());
									getConfig().set(zPath(uuid, args[1]), location.getZ());
									getConfig().set(yawPath(uuid, args[1]), location.getYaw());
									getConfig().set(pitchPath(uuid, args[1]), location.getPitch());
									saveConfig();
									lastUseTable.put(uuid, currentTime);
									audience.sendMessage(PluginConstants.info("현재 위치가 저장되었습니다."));
								}
							}
							else audience.sendMessage(NOT_READY);
						}
						else audience.sendMessage(PluginConstants.error("id는 영문, 숫자, -, _를 조합하여 16글자 이하로 입력해야 합니다."));
					}
					case "go" -> {
						if(args.length == 1) audience.sendMessage(NO_INPUT);
						else if(isReady(player)) {
							String homeDimension = getConfig().getString(worldPath(uuid, args[1]), "none");
							Location home = new Location(player.getWorld(), 0, 0, 0);
							String dim = switch(dimension) {
							case NETHER -> { yield "nether"; }
							case NORMAL -> { yield "overworld"; }
							case THE_END -> { yield "end"; }
							case CUSTOM -> { yield dimension.toString(); }
							};
							if(homeDimension.equals("none")) audience.sendMessage(NO_DATA);
							else if(homeDimension.equals(dim)) {
								double x = getConfig().getDouble(xPath(uuid, args[1]));
								double y = getConfig().getDouble(yPath(uuid, args[1]));
								double z = getConfig().getDouble(zPath(uuid, args[1]));
								float yaw = (float) getConfig().getDouble(yawPath(uuid, args[1]));
								float pitch = (float) getConfig().getDouble(pitchPath(uuid, args[1]));
								home.set(x, y, z);
								home.setYaw(yaw);
								home.setPitch(pitch);
								if(player.teleport(home)) {
									lastUseTable.put(uuid, currentTime);
									audience.sendMessage(PluginConstants.info("저장된 좌표로 이동했습니다."));
									player.removePotionEffect(PotionEffectType.INVISIBILITY);
									player.playSound(home, org.bukkit.Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.5F, 1F);
								}
								else audience.sendMessage(PluginConstants.error("알 수 없는 오류로 인해 이동하지 못했습니다."));
							}
							else audience.sendMessage(DIFFERENT_DIMENSION);
						}
						else audience.sendMessage(NOT_READY);
					}
					case "remove" -> {
						if(args.length == 1) audience.sendMessage(NO_INPUT);
						else {
							String type = getConfig().getString(worldPath(uuid, args[1]), "none");
							if(type.equals("overworld") || type.equals("nether") || type.equals("end")) {
								getConfig().set(uuid.toString() + "." + args[1], null);
								saveConfig();
								audience.sendMessage(PluginConstants.info("성공적으로 삭제되었습니다."));
							}
							else audience.sendMessage(NO_DATA);
						}
					}
					default -> sendCommandUsage(player);
					}
				}
			}
			else audience.sendMessage(PluginConstants.PLAYER_COMMAND);
			return true;
		}
		else return false;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if(command.getLabel().equals("home")) {
			if(sender instanceof Player) {
				if(args.length == 1) {
					return FIRST_ARGUMENTS.stream().filter(arg -> arg.startsWith(args[0].toLowerCase())).toList();
				}
				else if(args.length == 2 && FIRST_ARGUMENTS.contains(args[0].toLowerCase())) {
					Player player = (Player) sender;
					var section = getConfig().getConfigurationSection(player.getUniqueId().toString());
					if(section == null) return Collections.emptyList();
					else return section.getKeys(false)
							.stream().filter(home -> home.startsWith(args[1]) && !home.equals("time")).toList();
				}
			}
			else return Collections.emptyList();
		}
		return super.onTabComplete(sender, command, alias, args);
	}
	
	@EventHandler
	public void registerPlayer(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		lastUseTable.put(player.getUniqueId(), System.currentTimeMillis());
		if(getConfig().getConfigurationSection(player.getUniqueId().toString()) == null) {
			getConfig().set(player.getUniqueId().toString() + ".time", lastUseTable.get(player.getUniqueId()));
			saveConfig();
		}
	}
	
	@EventHandler
	public void playerTakeDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if(entity.getType().equals(EntityType.PLAYER)) {
			Player player = (Player) entity;
			lastUseTable.put(player.getUniqueId(), System.currentTimeMillis());
		}
	}
}
