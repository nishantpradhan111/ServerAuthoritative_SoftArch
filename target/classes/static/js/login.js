import { loadProfile, saveProfile } from "./common.js";

const form = document.querySelector("#login-form");
const input = document.querySelector("#name-input");

const profile = loadProfile();
if (profile.name) {
    input.value = profile.name;
}

form.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = input.value.trim() || "Guest";
    saveProfile({ name });
    window.location.href = "/room.html";
});