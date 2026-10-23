package vn.fs.service;
import vn.fs.dto.chat.ChatRequestDto;
import vn.fs.dto.chat.ChatResponseDto;
/** A single planner contract; production binds the validated structured pipeline. */
public interface ChatPlanner { ChatResponseDto reply(ChatRequestDto request,ChatDialogueMemory.State state); }
